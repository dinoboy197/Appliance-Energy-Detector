/*******************************************************************************
 * This file is part of the Appliance Energy Detector, a free household appliance energy disaggregation intelligence engine and webapp.
 * 
 * Copyright (C) 2011,2012 Taylor Raack <traack@raack.info>
 * 
 * The Appliance Energy Detector is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * The Appliance Energy Detector is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License along with the Appliance Energy Detector.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * According to sec. 7 of the GNU Affero General Public License, version 3, the terms of the AGPL are supplemented with the following terms:
 * 
 * If you modify this Program, or any covered work, by linking or combining it with any of the following programs (or modified versions of those libraries), containing parts covered by the terms of those libraries licenses, the licensors of this Program grant you additional permission to convey the resulting work:
 * 
 * Javabeans(TM) Activation Framework 1.1 (activation) - Common Development and Distribution License Version 1.0
 * AspectJ 1.6.9 (aspectjrt and aspectjweaver) - Eclipse Public License 1.0
 * EMMA 2.0.5312 (emma and emma_ant) - Common Public License Version 1.0
 * JAXB Project Libraries 2.2.2 (jaxb-api, jaxb-impl, jaxb-xjc) - Common Development and Distribution License Version 1.0
 * Java Standard Template Library 1.2 (jstl) - Common Development and Distribution License Version 1.0
 * Java Servlet Pages API 2.1 (jsp-api) - Common Development and Distribution License Version 1.0
 * Java Transaction API 1.1 (jta) - Common Development and Distribution License Version 1.0
 * JavaMail(TM) 1.4.1 (mail) - Common Development and Distribution License Version 1.0
 * XML Pull Parser 3 (xpp3) - Indiana University Extreme! Lab Software License Version 1.1.1
 * 
 * The interactive user interface of the software display an attribution notice containing the phrase "Appliance Energy Detector". Interactive user interfaces of unmodified and modified versions must display Appropriate Legal Notices according to sec. 5 of the GNU Affero General Public License, version 3, when you propagate an unmodified or modified version of the Program. In accordance with sec. 7 b) of the GNU Affero General Public License, version 3, these Appropriate Legal Notices must prominently display either a) "Initial Development by <a href='http://www.linkedin.com/in/taylorraack'>Taylor Raack</a>" if displayed in a web browser or b) "Initial Development by Taylor Raack (http://www.linkedin.com/in/taylorraack)" if displayed otherwise.
 ******************************************************************************/
package info.raack.appliancelabeler.machinelearning.weka;

import info.raack.appliancelabeler.machinelearning.MachineLearningEngine;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms.ApplianceEnergyConsumptionDetectionAlgorithm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import weka.classifiers.collective.CollectiveRandomizableSingleClassifierEnhancer;
import weka.classifiers.collective.meta.SimpleCollective;
import weka.classifiers.collective.meta.YATSI;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.UnsupportedAttributeTypeException;
import weka.core.Utils;

@Component
public class WekaMachineLearningEngine implements MachineLearningEngine {
	private Logger logger = LoggerFactory.getLogger(WekaMachineLearningEngine.class);
	
	private int currentId = 0;
	
	// need to keep each model separate, because multiple machine learning algorithms may use this weka adapter simulateously, so it needs to be thread-safe
	private Map<Integer,ModelStorage> modelsActive = new HashMap<Integer,ModelStorage>();
	
	// synchronized so that only one model can be loaded at a time, to prevent race conditions with incrementing the id
	public synchronized int loadModel(Serializable result) {
		currentId++;
		
		//String modelStr = (String)result;
		
		InputStream is = null;
		try {
			is = new ByteArrayInputStream((byte[])result);
			modelsActive.put(currentId,(ModelStorage)SerializationHelper.read(is));
			is.close();
			return currentId;
		} catch (Exception e) {
			throw new RuntimeException("Could not deserialize the Weka model back to a Classifier", e);
		} finally {
			if(is != null) {
				try {
					is.close();
				}
				catch (IOException e) {
					logger.warn("Could not close bais for serialized model, but we don't care", e);
				}
			}
		}
	}
	
	public void releaseModel(int modelId) {
		modelsActive.remove(modelId);
	}

	public Serializable buildModel(MODEL_TYPE modelType, List<ATTRIBUTE_TYPE> attributeTypes, List<String> attributeNames, List<double[]> mlData) {
		// actually build the classifier
		CollectiveRandomizableSingleClassifierEnhancer classifier = null;
		
		Instances[] instances = createInstances(attributeTypes, attributeNames, mlData);
		
		if(modelType == MODEL_TYPE.YATSI) {
			classifier = new YATSI();
		} else if(modelType == MODEL_TYPE.SIMPLE) {
			classifier = new SimpleCollective();
		} else {
			throw new RuntimeException("Does not currently support " + modelType);
		}
		
		logger.debug("Building ml classifier");
		
		//logInstances(instances);
		try {
			classifier.buildClassifier(instances[0], instances[1]);
		}
		catch (UnsupportedAttributeTypeException e2) {
			if(e2.getMessage().equals("weka.classifiers.collective.meta.YATSI: Cannot handle unary class!")) {
				// just return -1 if we can't predict because we don't have more than one class label
				return null;
			} else {
				throw new RuntimeException("Could not build classifier", e2);
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Could not build classifier", e);
		}
		
		logger.debug("Done building ml classifier");
		
		ModelStorage modelStorage = new ModelStorage();
		modelStorage.setModel(classifier);
		modelStorage.setTrainingData(instances[0]);
	      
		// serialize the classifier so that it can be written to the database
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			SerializationHelper.write(baos, modelStorage);
		}
		catch (Exception e) {
			throw new RuntimeException("Could not serialize the classifier to save to the database", e);
		}
		
		// fix this exporting / loading later!
		return baos.toByteArray();
	}

	// create instances for the engine, separating labeled and unlabeled data into training and test sets, respectively - this is how the algorithms work with labeled and unlabeled data
	private Instances[] createInstances(List<ATTRIBUTE_TYPE> attributeTypes, List<String> attributeNames, List<double[]> mlData) {
		if(attributeTypes.size() != attributeNames.size()) {
			throw new IllegalArgumentException("Attribute types size of " + attributeTypes.size() + " does not match attribute names size of " + attributeNames.size());
		}

		ArrayList<Attribute> attribInfo = new ArrayList<Attribute>();
		
		Map<Integer,List<String>> memoizedAttributeValues = new HashMap<Integer,List<String>>();
		
		for(int i = 0; i < attributeTypes.size(); i++) {
			Attribute attribute = null;
			if(attributeTypes.get(i) == ATTRIBUTE_TYPE.NUMERIC) {
				attribute = new Attribute(attributeNames.get(i));
			} else {
				attribute = new Attribute(attributeNames.get(i), extractValuesForAttributeIndex(i, mlData, memoizedAttributeValues));
			}
			
			attribInfo.add(attribute);
		}
		
		Instances instanceArray[] = new Instances[2];
		
		Instances labeledInstances = new Instances("labeled", attribInfo, 0);
		Instances unlabeledInstances = new Instances("unlabeleded", attribInfo, 0);
		
		int classIndex = attributeTypes.size() - 1;
		
		for(double[] data : mlData) {
			
			double[] values = new double[attributeNames.size()];
			
			for(int i = 0; i < data.length; i++) {
				if(attributeTypes.get(i) == ATTRIBUTE_TYPE.NUMERIC) {
					values[i] = ApplianceEnergyConsumptionDetectionAlgorithm.missingValue != data[i] ? data[i] : Utils.missingValue();
				} else {
					double value = attribInfo.get(i).indexOfValue(data[i] + "");
					values[i] = value >= 0 ? value : Utils.missingValue();
				}
			}
			
			if(Utils.isMissingValue(data[classIndex])) {
				unlabeledInstances.add(new DenseInstance(1.0, values));
			} else {
				labeledInstances.add(new DenseInstance(1.0, values));
			}
		}
		
		// add the class label - the user appliance should be the last attribute
		labeledInstances.setClassIndex(classIndex);
		unlabeledInstances.setClassIndex(classIndex);
		
		instanceArray[0] = labeledInstances;
		instanceArray[1] = unlabeledInstances;
		
		return instanceArray;
	}

	private List<String> extractValuesForAttributeIndex(int index, List<double[]> mlData, Map<Integer,List<String>> memoizedValues) {
		if(memoizedValues.containsKey(index)) {
			return memoizedValues.get(index);
		} else {
			// perform memoization
			Set<String> uniqueValues = new HashSet<String>();
			for(double[] datapoint : mlData) {
				uniqueValues.add(datapoint[index] + "");
			}
			
			// remove missing class
			uniqueValues.remove(Double.valueOf(Utils.missingValue()).toString());
			
			memoizedValues.put(index, new ArrayList<String>(uniqueValues));
			return memoizedValues.get(index);
		}
	}

	public int predictWithModel(int modelId, double[] variables, double minProbability) {
		ModelStorage modelStorage = modelsActive.get(modelId);
		
		if(modelStorage == null) {
			throw new RuntimeException("No classifier loaded with modelId " + modelId);
		}
		
		// add unknown label
		double[] fullList = ArrayUtils.add(variables, Utils.missingValue());
		
		Instance instance = new DenseInstance(1.0, fullList);
		
		Instances trainingData = modelStorage.getTrainingData();
		
		instance.setDataset(trainingData);
		try {
			double[] distributionForUserApplianceIdIndices = modelStorage.getModel().distributionForInstance(instance);
			
			// need to have probability of at least 70%
			// since the probabilities must sum to 1, if we see a probability >= 0.7, we are guaranteed that it is the largest and only one
			int userApplianceIndex = -1;
			double probability = 0;
			for(int i = 0; i < distributionForUserApplianceIdIndices.length; i++) {
				if(distributionForUserApplianceIdIndices[i] >= minProbability && distributionForUserApplianceIdIndices[i] >= probability) {
					userApplianceIndex = i;
					probability = distributionForUserApplianceIdIndices[i];
				}
			}
			
			if(userApplianceIndex >= 0) {
				int userApplianceId = (int)Double.parseDouble(trainingData.attribute(trainingData.classIndex()).value((int)userApplianceIndex));
				if(userApplianceId == 0) {
					int j = 6;
				}
				return userApplianceId;
			} else {
				return -1;
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Could not classify instance", e);
		}
	}

	@Override
	public List<double[]> getTrainingInstancesWithClassLabels(int modelId) {
		ModelStorage modelStorage = modelsActive.get(modelId);
		
		if(modelStorage == null) {
			throw new RuntimeException("No classifier loaded with modelId " + modelId);
		}
		
		Instances trainingInstances = modelStorage.getTrainingData();
		
		List<double[]> transformedTrainingInstances = new ArrayList<double[]>();
		
		for(Instance trainingInstance : trainingInstances) {
			if(!trainingInstance.classIsMissing()) {
				double[] transformedTrainingInstance = new double[trainingInstance.numAttributes()];
				
				// iterating across all attributes should give all attributes and class value
				for(int i = 0; i < trainingInstance.numAttributes(); i++) {
					transformedTrainingInstance[i] = trainingInstance.value(i);
				}
				
				transformedTrainingInstances.add(transformedTrainingInstance);
			}
		}
		
		return transformedTrainingInstances;
	}
}

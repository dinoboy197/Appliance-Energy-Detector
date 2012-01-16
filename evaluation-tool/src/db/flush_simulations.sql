delete from simulated_appliance_energy_consumption_values;
delete from simulated_appliance_state_transitions;
delete from simulated_appliances;
delete from simulations;

delete ast.* from appliance_state_transitions ast, user_appliances ua, energy_monitors em where em.monitor_type = 'simulator' and em.user_id = 'simulator' and em.id = ua.energy_monitor_id and ast.user_appliance_id = ua.id;

delete aecp.* from appliance_energy_consumption_predictions aecp, user_appliances ua, energy_monitors em where em.monitor_type = 'simulator' and em.user_id = 'simulator' and em.id = ua.energy_monitor_id and aecp.user_appliance_id = ua.id;

delete m.* from energy_measurements m, energy_monitors em where em.monitor_type = 'simulator' and em.user_id = 'simulator' and em.id = m.energy_monitor_id;

delete ua.* from user_appliances ua, energy_monitors em where em.monitor_type = 'simulator' and em.user_id = 'simulator' and em.id = ua.energy_monitor_id;

delete am.* from algorithm_models am, energy_monitors em where em.monitor_type = 'simulator' and em.user_id = 'simulator' and em.id = am.energy_monitor_id;

delete from energy_monitors where monitor_type = 'simulator' and user_id = 'simulator';

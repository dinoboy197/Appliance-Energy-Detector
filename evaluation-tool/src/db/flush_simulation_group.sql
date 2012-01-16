delete saecv.* from simulated_appliance_energy_consumption_values saecv, simulated_appliances sa, simulations s where saecv.simulated_appliance_id = sa.id and sa.simulation_id = s.id and s.simulation_group_id = ?;
delete sast.* from simulated_appliance_state_transitions sast, simulated_appliances sa, simulations s where sast.simulated_appliance_id = sa.id and sa.simulation_id = s.id and s.simulation_group_id = ?;
delete sa.* from simulated_appliances sa, simulations s where sa.simulation_id = s.id and s.simulation_group_id = ?;
delete from simulations where simulation_group_id = ?;

delete ast.* from appliance_state_transitions ast, user_appliances ua, energy_monitors em, simulations s where em.monitor_id = s.id and s.simulation_group_id = ? and em.id = ua.energy_monitor_id and ast.user_appliance_id = ua.id;

delete aecp.* from appliance_energy_consumption_predictions aecp, user_appliances ua, energy_monitors em, simulations s where em.monitor_id = s.id and s.simulation_group_id = ? and em.id = ua.energy_monitor_id and aecp.user_appliance_id = ua.id;

delete m.* from energy_measurements m, energy_monitors em, simulations s where em.monitor_id = s.id and s.simulation_group_id = ? and em.id = m.energy_monitor_id;

delete ua.* from user_appliances ua, energy_monitors em, simulations s where em.monitor_id = s.id and s.simulation_group_id = ? and em.id = ua.energy_monitor_id;

delete am.* from algorithm_models am, energy_monitors em, simulations s where em.monitor_id = s.id and s.simulation_group_id = ? and em.id = am.energy_monitor_id;

delete em.* from energy_monitors em, simulations s where em.monitor_id = s.id and s.simulation_group_id = ?;

delete from simulation_groups where id = ?

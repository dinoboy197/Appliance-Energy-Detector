drop table energy_measurements;

create table energy_measurements (
	energy_monitor_id bigint unsigned not null, -- linked to energy_monitor table
	reading_time bigint not null,
	power integer,
	voltage numeric(6,2),
	primary key (energy_monitor_id, reading_time),
	foreign key (energy_monitor_id) references energy_monitors(id)
) engine=innodb;

update energy_monitors set last_measurement_offset = -1;

delete from appliance_state_transitions;
alter table appliance_state_transitions modify column time bigint not null;


delete from appliance_energy_consumption_predictions;
alter table appliance_energy_consumption_predictions modify column start_time bigint not null;
alter table appliance_energy_consumption_predictions modify column end_time bigint not null;

delete from simulated_appliance_energy_consumption_values;
alter table simulated_appliance_energy_consumption_values modify column start_time bigint not null;
alter table simulated_appliance_energy_consumption_values modify column end_time bigint not null;

delete from simulated_appliances;

delete from simulations;
alter table simulations modify column start_time bigint not null;

delete from appliance_energy_labels;
alter table appliance_energy_labels modify column creation_time bigint not null;
alter table appliance_energy_labels modify column ted_time bigint not null;


alter table algorithm_models drop column data;

create table simulation_groups (
	id serial primary key,
	start_time bigint not null,
	duration int not null, -- seconds of simulation
	num_appliances int not null, -- number of appliances simulated
	on_concurrency int not null, -- max number of appliances on at once
	labels_per_appliance int not null -- max number of on / off labels generated per appliance
) engine=innodb;


alter table simulations add column simulation_group_id bigint unsigned null;
alter table simulations add foreign key (simulation_group_id) references simulation_groups (id);

alter table simulations add column done boolean not null;
update simulations set done = true;
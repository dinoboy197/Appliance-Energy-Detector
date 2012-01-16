alter table appliance_energy_consumption_predictions modify energy_consumed int not null;
alter table simulated_appliance_energy_consumption_values modify energy_consumed int not null;

alter table simulated_appliances change column type class varchar(256) not null;

create table algorithm_models (
	id serial primary key,
	energy_monitor_id bigint unsigned not null, -- linked to energy monitors table
	detection_algorithm smallint unsigned null, -- only null if provided by user
	data longblob not null, -- model for the algorithm
	unique key (energy_monitor_id, detection_algorithm),
	foreign key (energy_monitor_id) references energy_monitors(id)
) engine=innodb;

alter table user_oauth_tokens add column user_email varchar(256) not null;

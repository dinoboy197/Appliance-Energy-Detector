-- if mysql
-- SET storage_engine=INNODB;
-- change link columns from int to bigint unsigned

create table appliances (
	id serial primary key,
	description varchar(512) not null, -- 'refrigerator' or 'air conditioner'
	sort_order int not null
) engine=innodb;

insert into appliances (description, sort_order) values ('Electric Space Heater',1),('Central Air Conditioner',2),
('Window/Wall Air Conditioner',3),('Electric Water Heater',4),('Refrigerator',5),('Freezer (separate from refrigerator)',6),
('Clothes Washer',7),('Electric Clothes Dryer',8),('Natural Gas Clothes Dryer',9),('Dishwasher',10),('Electric Range/Stove/Oven',11),
('Microwave Oven',12),('Television',13),('Personal Computer',14);

create table energy_monitors (
	id serial primary key,
	user_id varchar(128) not null, -- stepgreen user id
	monitor_type varchar(128) not null, -- probably ted5000
	monitor_id varchar(128) not null, -- id for the device and mtu; for teds, it will be like <tedid>_<mtuid>
	last_measurement_offset bigint not null,
	unique (user_id, monitor_type, monitor_id)
) engine=innodb;

-- note that there is NO unique key on (reading_time, energy_monitor_id) - enforcing this uniqueness across all time and all rows (potentially billions) would take too long. there is a weak enforcement of this in software
create table energy_measurements (
	energy_monitor_id bigint unsigned not null, -- linked to energy_monitor table
	reading_time bigint not null,
	power integer,
	voltage numeric(6,2),
	primary key (energy_monitor_id, reading_time),
	foreign key (energy_monitor_id) references energy_monitors(id)
) engine=innodb;

create table user_appliances (
	id serial primary key,
	energy_monitor_id bigint unsigned not null, -- linked to energy monitors table
	appliance_id bigint unsigned not null, -- linked to appliances table
	name varchar(128) not null, -- name of the individual appliance for the user 'GE fridge 720'
	algorithm_id int null, -- id of algorithm which created this appliance, if any
	algorithm_generated bool not null, -- was this user appliance created automatically by the algorithm?
	foreign key (appliance_id) references appliances(id),
	foreign key (energy_monitor_id) references energy_monitors(id)
) engine=innodb;

-- this table contains the data point features themselves (no labels)
create table appliance_state_transitions (
	id serial primary key,
	user_appliance_id bigint unsigned not null, -- linked to user_appliance table
	detection_algorithm smallint unsigned null, -- only null if provided by user
	time bigint not null,
	start_on bool not null,
	index (user_appliance_id,detection_algorithm)
) engine=innodb;

-- this table contains predicted energy consumed by each user appliance in discrete timesteps
create table appliance_energy_consumption_predictions (
	id serial primary key,
	detection_algorithm smallint unsigned not null,
	user_appliance_id bigint unsigned not null, -- linked to user_appliances table
	start_time bigint not null, -- start time of energy consumption window
	end_time bigint not null, -- start time of energy consumption window
	energy_consumed int not null, -- energy consumed by this user_appliance between start_time and end_time - in watt-seconds (joules)
	unique key (detection_algorithm,user_appliance_id,start_time),
	foreign key(user_appliance_id) references user_appliances(id)
) engine=innodb;

create table algorithm_models (
	id serial primary key,
	energy_monitor_id bigint unsigned not null, -- linked to energy monitors table
	detection_algorithm smallint unsigned null, -- only null if provided by user
	unique key (energy_monitor_id, detection_algorithm),
	foreign key (energy_monitor_id) references energy_monitors(id)
) engine=innodb;

create table simulations (
	id varchar(128) primary key,
	start_time bigint not null,
	duration int not null, -- seconds of simulation
	num_appliances int not null, -- number of appliances simulated
	on_concurrency int not null, -- max number of appliances on at once
	labels_per_appliance int not null, -- max number of on / off labels generated per appliance
	done boolean not null, -- is this simulation done
	simulation_group_id bigint unsigned null, -- link to simulation groups table, if this is part of a simulation group
	foreign key (simulation_group_id) references 
) engine=innodb;

create table simulated_appliances (
	id serial primary key,
	simulation_id varchar(128) not null, -- link to the simulation
	labeled_appliance_id bigint unsigned, -- link to the actual user appliance which was generated via a label
	class varchar(256) not null, -- java class of simulated appliance
	appliance_num int not null, -- number of the simulated appliance in the simulation
	unique key (simulation_id, labeled_appliance_id),
	foreign key (simulation_id) references simulations(id),
	foreign key (labeled_appliance_id) references user_appliances(id)
) engine=innodb;

create table simulated_appliance_state_transitions (
	id serial primary key,
	simulated_appliance_id bigint unsigned not null,
	time bigint not null,
	start_on bool not null,
	index (simulated_appliance_id)
) engine=innodb;

create table simulated_appliance_energy_consumption_values (
	id serial primary key,
	simulated_appliance_id bigint unsigned not null, -- linked to simulated_appliances table
	start_time bigint not null, -- start time of energy consumption window
	end_time bigint not null, -- start time of energy consumption window
	energy_consumed int not null, -- energy consumed by this simulated_appliance between start_time and end_time in watt-seconds (joules)
	foreign key(simulated_appliance_id) references simulated_appliances(id)
) engine=innodb;

create table simulation_groups (
	id serial primary key,
	start_time bigint not null,
	duration int not null, -- seconds of simulation
	num_appliances int not null, -- number of appliances simulated
	on_concurrency int not null, -- max number of appliances on at once
	labels_per_appliance int not null -- max number of on / off labels generated per appliance
) engine=innodb;
	
create table appliance_energy_labels (
	id serial primary key,
	user_appliance_id bigint unsigned not null, -- linked to user_appliances table
	label_type integer not null, -- 1 for start, 0 for stop
	creation_time timestamp default NOW() not null, -- time that this entry was created
	ted_time timestamp not null, -- time of the ted from the live data feed
	foreign key (user_appliance_id) references user_appliances(id)
) engine=innodb;

create table persistent_logins ( -- for spring security to remember logins
	username varchar(64) not null,
	series varchar(64) primary key,
	token varchar(64) not null,
	last_used timestamp not null
) engine=innodb;

create table user_oauth_tokens (
	user_id varchar(128) primary key,
	user_email varchar(256) not null,
	spring_oauth_serialized_token_map blob
) engine=innodb;

create table energy_costs (
	energy_monitor_id bigint unsigned not null, -- linked to energy_monitor table
	cost_per_kwh numeric(6,4),
	primary key (energy_monitor_id),
	foreign key (energy_monitor_id) references energy_monitors(id)
) engine=innodb;
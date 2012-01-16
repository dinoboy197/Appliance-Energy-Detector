begin;

create table energy_costs (
	energy_monitor_id bigint unsigned not null, -- linked to energy_monitor table
	cost_per_kwh numeric(6,4),
	primary key (energy_monitor_id),
	foreign key (energy_monitor_id) references energy_monitors(id)
) engine=innodb;

commit;


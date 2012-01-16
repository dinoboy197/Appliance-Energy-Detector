begin;

alter table user_appliances add column algorithm_generated bool not null;
insert into appliances (description, sort_order) values ('Other', 15);


create table simulated_appliance_state_transitions (
	id serial primary key,
	simulated_appliance_id bigint unsigned not null,
	time bigint not null,
	start_on bool not null,
	index (simulated_appliance_id)
) engine=innodb;

commit;
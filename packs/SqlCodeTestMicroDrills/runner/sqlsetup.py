SETUP = """
CREATE TABLE partitions(topic TEXT, partition_id INT, log_end_offset INT);
CREATE TABLE commits(group_id TEXT, topic TEXT, partition_id INT, committed_offset INT);
INSERT INTO partitions VALUES ('orders',0,100),('orders',1,250),('payments',0,40),('audit',0,10),('empty',0,5);
INSERT INTO commits VALUES
 ('billing','orders',0,90),('billing','orders',1,200),('billing','payments',0,40),
 ('fraud','payments',0,10),('fraud','orders',0,80),
 ('archive','audit',0,10),
 ('analytics','orders',1,210),('analytics','orders',0,100),
 ('zeta','orders',0,40);
"""

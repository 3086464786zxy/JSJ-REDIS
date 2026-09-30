UPDATE sys_menu SET type='1', name='Audit', path='/audit', url='views/system/Audit/AuditList.vue', icon='Document'
WHERE code='sys:audit:list' AND type='2';
INSERT INTO sys_menu(parent_id,title,code,name,path,url,type,icon,order_num,create_time)
SELECT 0,'操作审计','sys:audit:list','Audit','/audit','views/system/Audit/AuditList.vue','1','Document',99,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE code='sys:audit:list');
UPDATE authz_state SET version=version+1 WHERE state_id=1;

-- Used only when the menu table is empty.
INSERT INTO sys_menu(menu_id,parent_id,title,code,name,path,url,type,icon,order_num)
VALUES
(1,0,'系统管理','sys:system','System','/system','Layout','0','Setting','1'),
(2,1,'用户管理','sys:user,sys:user:list','UserList','/system/user','views/system/User/UserList.vue','1','User','1'),
(3,1,'角色管理','sys:role,sys:role:list','RoleList','/system/role','views/system/Role/RoleList.vue','1','UserFilled','2'),
(4,1,'菜单管理','sys:menu,sys:menu:list','MenuList','/system/menu','views/system/Menu/MenuList.vue','1','Menu','3'),
(5,2,'新增用户','sys:user:add',NULL,NULL,NULL,'2',NULL,'1'),
(6,2,'编辑用户','sys:user:edit',NULL,NULL,NULL,'2',NULL,'2'),
(7,2,'删除用户','sys:user:delete',NULL,NULL,NULL,'2',NULL,'3'),
(8,2,'重置密码','sys:user:reset',NULL,NULL,NULL,'2',NULL,'4'),
(9,3,'新增角色','sys:role:add',NULL,NULL,NULL,'2',NULL,'1'),
(10,3,'编辑角色','sys:role:edit',NULL,NULL,NULL,'2',NULL,'2'),
(11,3,'删除角色','sys:role:delete',NULL,NULL,NULL,'2',NULL,'3'),
(12,3,'分配权限','sys:role:assign',NULL,NULL,NULL,'2',NULL,'4'),
(13,4,'新增菜单','sys:menu:add',NULL,NULL,NULL,'2',NULL,'1'),
(14,4,'编辑菜单','sys:menu:edit',NULL,NULL,NULL,'2',NULL,'2'),
(15,4,'删除菜单','sys:menu:delete',NULL,NULL,NULL,'2',NULL,'3'),
(16,1,'查询审计','sys:audit:list',NULL,NULL,NULL,'2',NULL,'4');

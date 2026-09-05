INSERT INTO admin_roles (name, description, is_active)
VALUES ('SUPER_ADMIN', 'Unrestricted MoodBuds administration', 1)
ON DUPLICATE KEY UPDATE description = VALUES(description), is_active = 1;

INSERT INTO admin_permissions (permission_key, module, description) VALUES
('admins.read', 'admins', 'View administrators'),
('admins.manage', 'admins', 'Create and manage administrators'),
('roles.read', 'rbac', 'View roles and permissions'),
('roles.manage', 'rbac', 'Create roles and assign permissions'),
('catalog.read', 'catalog', 'View catalogue administration data'),
('catalog.manage', 'catalog', 'Create and manage catalogue data'),
('inventory.read', 'inventory', 'View inventory and stock history'),
('inventory.manage', 'inventory', 'Adjust product inventory'),
('moods.manage', 'moods', 'Manage moods and product mood tags'),
('quiz.manage', 'quiz', 'Manage quiz paths, questions, options and weights'),
('coupons.manage', 'coupons', 'Create and manage coupons'),
('orders.read', 'orders', 'View orders'),
('orders.manage', 'orders', 'Change order status'),
('returns.read', 'returns', 'View returns'),
('returns.manage', 'returns', 'Review and update returns'),
('customers.read', 'customers', 'View customers'),
('customers.manage', 'customers', 'Activate or deactivate customers'),
('reports.read', 'reports', 'View operational and financial reports'),
('audit.read', 'audit', 'View administrator audit history')
ON DUPLICATE KEY UPDATE module = VALUES(module), description = VALUES(description);

INSERT IGNORE INTO admin_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM admin_roles r
CROSS JOIN admin_permissions p
WHERE r.name = 'SUPER_ADMIN';

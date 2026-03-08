const bcrypt = require('bcryptjs');
const users = {
  admin: '$2a$10$VkM2GgSvD8lqed0wnw/BaOe7RCHXamCsm7WZQo3Y6g7qOkalPcaBi',
  Dr_zhang: '$2a$10$/rpxXa8nP.N2PL.19sX8n.IAJTJsaHbsgIKvEybZa.NxRlYgFA/qq',
  Dr_zhao: '$2a$10$wmoQo7mXm3rfUgbdKkrVGOsoO/xMGJq6oYwRKQ7L6AKZVXZU8wb46'
};
const candidates = ['fb251800','Fb251800','123456','12345678','admin123','admin123456','Dr_zhang','Dr_zhao','zhang123','zhao123','Gauss@1234'];
for (const [u,h] of Object.entries(users)) {
  for (const p of candidates) {
    if (bcrypt.compareSync(p,h)) console.log(u + '\t' + p);
  }
}

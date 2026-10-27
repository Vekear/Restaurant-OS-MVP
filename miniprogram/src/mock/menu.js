/** 演示模式：内置菜单数据（与后端种子数据大致一致：6 个分类 / 15 道菜） */
export const MOCK_CATEGORIES = [
  { id: 1, name: '招牌推荐', sort: 1 },
  { id: 2, name: '热菜', sort: 2 },
  { id: 3, name: '凉菜', sort: 3 },
  { id: 4, name: '主食', sort: 4 },
  { id: 5, name: '汤品', sort: 5 },
  { id: 6, name: '饮品', sort: 6 }
]

export const MOCK_DISHES = [
  {
    id: 101,
    categoryId: 1,
    name: '老坛酸菜鱼',
    description: '现杀黑鱼片，酸菜脆爽，微辣开胃',
    price: 68,
    sales: 328,
    stock: 40,
    image: '/static/demo/dish-4.png',
    tags: ['招牌', '微辣']
  },
  {
    id: 102,
    categoryId: 1,
    name: '本帮红烧肉',
    description: '五花三层，慢火收汁，甜咸适口',
    price: 48,
    sales: 256,
    stock: 30,
    image: '/static/demo/dish-1.png',
    tags: ['招牌']
  },
  {
    id: 103,
    categoryId: 1,
    name: '宫保鸡丁',
    description: '鸡腿肉丁配油酥花生，糊辣荔枝口',
    price: 38,
    sales: 412,
    stock: 60,
    image: '/static/demo/dish-3.png',
    tags: ['热销', '微辣']
  },
  {
    id: 104,
    categoryId: 2,
    name: '水煮牛肉',
    description: '现片牛肉，麻辣鲜香，配黄豆芽',
    price: 58,
    sales: 188,
    stock: 25,
    image: '/static/demo/dish-4.png',
    tags: ['中辣']
  },
  {
    id: 105,
    categoryId: 2,
    name: '干锅花菜',
    description: '有机花菜配五花肉片，锅气十足',
    price: 32,
    sales: 143,
    stock: 35,
    image: '/static/demo/dish-2.png',
    tags: []
  },
  {
    id: 106,
    categoryId: 2,
    name: '麻婆豆腐',
    description: '嫩豆腐配牛肉末，麻辣烫嫩',
    price: 26,
    sales: 221,
    stock: 50,
    image: '/static/demo/dish-1.png',
    tags: ['微辣']
  },
  {
    id: 107,
    categoryId: 2,
    name: '清炒时蔬',
    description: '当日时令蔬菜，少油快炒',
    price: 22,
    sales: 96,
    stock: 45,
    image: '/static/demo/dish-2.png',
    tags: ['清淡']
  },
  {
    id: 108,
    categoryId: 3,
    name: '口水鸡',
    description: '土鸡腿肉，红油藤椒汁，冰镇爽口',
    price: 28,
    sales: 175,
    stock: 30,
    image: '/static/demo/dish-3.png',
    tags: ['微辣']
  },
  {
    id: 109,
    categoryId: 3,
    name: '手拍黄瓜',
    description: '现拍现拌，蒜香清爽',
    price: 16,
    sales: 260,
    stock: 60,
    image: '/static/demo/dish-2.png',
    tags: ['清淡']
  },
  {
    id: 110,
    categoryId: 4,
    name: '扬州炒饭',
    description: '隔夜米粒配虾仁火腿，粒粒分明',
    price: 22,
    sales: 302,
    stock: 80,
    image: '/static/demo/dish-3.png',
    tags: ['热销']
  },
  {
    id: 111,
    categoryId: 4,
    name: '手工葱油面',
    description: '现擀面条，葱油喷香，配溏心蛋',
    price: 18,
    sales: 168,
    stock: 60,
    image: '/static/demo/dish-1.png',
    tags: []
  },
  {
    id: 112,
    categoryId: 5,
    name: '番茄蛋花汤',
    description: '沙瓤番茄慢熬，蛋花细嫩',
    price: 18,
    sales: 120,
    stock: 40,
    image: '/static/demo/dish-5.png',
    tags: ['清淡']
  },
  {
    id: 113,
    categoryId: 5,
    name: '酸辣汤',
    description: '胡椒酸醋提味，豆腐木耳笋丝',
    price: 20,
    sales: 88,
    stock: 40,
    image: '/static/demo/dish-5.png',
    tags: ['微辣']
  },
  {
    id: 114,
    categoryId: 6,
    name: '鲜榨橙汁',
    description: '当日鲜橙现榨，无添加糖',
    price: 12,
    sales: 210,
    stock: 100,
    image: '/static/demo/dish-6.png',
    tags: ['冰饮']
  },
  {
    id: 115,
    categoryId: 6,
    name: '柠檬冰茶',
    description: '锡兰红茶配香水柠檬，酸甜解腻',
    price: 10,
    sales: 244,
    stock: 100,
    image: '/static/demo/dish-6.png',
    tags: ['冰饮']
  }
]

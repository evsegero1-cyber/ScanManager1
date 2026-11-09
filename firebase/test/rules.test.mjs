import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment, assertSucceeds, assertFails,
} from '@firebase/rules-unit-testing';
import {
  doc, getDoc, setDoc, updateDoc, deleteDoc, addDoc, collection, increment, writeBatch, runTransaction,
} from 'firebase/firestore';

let env;
const USER = 'user1';
const OTHER = 'user2';
const ADMIN = 'admin1';

const baseUser = (o = {}) => ({
  email: 'u@x.ru', firstName: 'Иван', lastName: 'Петров', role: 'USER', level: 1, experience: 0,
  viewedProductsCount: 0, hasAddedToRoute: false, photoBase64: '', frameType: '', ...o,
});

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-scanmanager',
    firestore: { rules: readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
  });
});
after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users', USER), baseUser());
    await setDoc(doc(db, 'users', OTHER), baseUser({ email: 'o@x.ru' }));
    await setDoc(doc(db, 'users', ADMIN), baseUser({ email: 'a@x.ru', role: 'ADMIN' }));
    for (const [id, xp] of [['13', 50], ['14', 100], ['15', 200], ['16', 30], ['99', 1000]]) {
      await setDoc(doc(db, 'achievements', id), { title: 'A' + id, description: '', hidden: false, experienceReward: xp });
    }
    await setDoc(doc(db, 'routes', 'r1'), { name: 'Маршрут 1' });
    await setDoc(doc(db, 'categories', 'c1'), { name: 'Bags' });
  });
});

const as = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();

// ---------- Роли и профиль ----------
test('пользователь не может сделать себя админом', async () => {
  await assertFails(updateDoc(doc(as(USER), 'users', USER), { role: 'ADMIN' }));
});

test('пользователь не может накрутить себе опыт и уровень', async () => {
  await assertFails(updateDoc(doc(as(USER), 'users', USER), { experience: 5000, level: 11 }));
  await assertFails(updateDoc(doc(as(USER), 'users', USER), { level: 20 }));
});

test('регистрация только с ролью USER и нулевым прогрессом', async () => {
  const db = as('new1');
  await assertFails(setDoc(doc(db, 'users', 'new1'), baseUser({ role: 'ADMIN' })));
  await assertFails(setDoc(doc(db, 'users', 'new1'), baseUser({ experience: 999 })));
  await assertFails(setDoc(doc(db, 'users', 'new2'), baseUser()));
  await assertSucceeds(setDoc(doc(db, 'users', 'new1'), baseUser()));
});

test('свои фото, имя, рамку можно менять; чужие — нет', async () => {
  await assertSucceeds(updateDoc(doc(as(USER), 'users', USER), { photoBase64: 'abc' }));
  await assertSucceeds(updateDoc(doc(as(USER), 'users', USER), { firstName: 'Пётр', lastName: 'Иванов' }));
  await assertSucceeds(updateDoc(doc(as(USER), 'users', USER), { frameType: '' }));
  await assertFails(updateDoc(doc(as(USER), 'users', OTHER), { photoBase64: 'abc' }));
});

test('рамка доступна только с нужного уровня', async () => {
  await assertFails(updateDoc(doc(as(USER), 'users', USER), { frameType: 'gold' }));
  await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), 'users', USER), { level: 5 }));
  await assertSucceeds(updateDoc(doc(as(USER), 'users', USER), { frameType: 'gold' }));
  await assertFails(updateDoc(doc(as(USER), 'users', USER), { frameType: 'ice' }));
});

test('счётчик просмотров растёт только на 1', async () => {
  await assertSucceeds(updateDoc(doc(as(USER), 'users', USER), { viewedProductsCount: increment(1) }));
  await assertFails(updateDoc(doc(as(USER), 'users', USER), { viewedProductsCount: 100 }));
});

test('незалогиненный ничего не читает', async () => {
  await assertFails(getDoc(doc(anon(), 'users', USER)));
  await assertFails(getDoc(doc(anon(), 'categories', 'c1')));
});

// ---------- Авто-достижения ----------
async function autoGrant(db, uid, aid, expBefore, reward) {
  return runTransaction(db, async (tx) => {
    const exp = expBefore + reward;
    tx.set(doc(db, 'users', uid, 'achievements', aid), { earned: true, achievementId: aid });
    tx.update(doc(db, 'users', uid), { experience: exp, level: Math.floor(exp / 500) + 1, lastAutoAchievement: aid });
  });
}

test('авто-достижение за 10 просмотров выдаётся с правильным опытом', async () => {
  await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), 'users', USER), { viewedProductsCount: 10 }));
  await assertSucceeds(autoGrant(as(USER), USER, '13', 0, 50));
  const snap = await env.withSecurityRulesDisabled(async (c) => (await getDoc(doc(c.firestore(), 'users', USER))).data());
  if (snap.experience !== 50) throw new Error('опыт не начислен: ' + snap.experience);
});

test('опыт накапливается (исправлен баг старой версии)', async () => {
  await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), 'users', USER), { viewedProductsCount: 60, experience: 480, level: 1 }));
  await assertSucceeds(autoGrant(as(USER), USER, '13', 480, 50));
  await assertSucceeds(autoGrant(as(USER), USER, '14', 530, 100));
  const d = await env.withSecurityRulesDisabled(async (c) => (await getDoc(doc(c.firestore(), 'users', USER))).data());
  if (d.experience !== 630 || d.level !== 2) throw new Error(JSON.stringify(d));
});

test('нельзя получить авто-достижение без выполнения условия', async () => {
  await assertFails(autoGrant(as(USER), USER, '13', 0, 50));       // 0 просмотров
  await assertFails(autoGrant(as(USER), USER, '16', 0, 30));       // не добавлял в маршрут
});

test('нельзя выдать себе обычное достижение или завысить награду', async () => {
  await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), 'users', USER), { viewedProductsCount: 10 }));
  await assertFails(autoGrant(as(USER), USER, '99', 0, 1000));
  await assertFails(autoGrant(as(USER), USER, '13', 0, 5000));
});

test('нельзя получить одно авто-достижение дважды', async () => {
  await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), 'users', USER), { viewedProductsCount: 10 }));
  await assertSucceeds(autoGrant(as(USER), USER, '13', 0, 50));
  await assertFails(autoGrant(as(USER), USER, '13', 50, 50));
});

test('первый товар в маршруте даёт достижение 16', async () => {
  await assertSucceeds(updateDoc(doc(as(USER), 'users', USER), { hasAddedToRoute: true }));
  await assertSucceeds(autoGrant(as(USER), USER, '16', 0, 30));
});

// ---------- Админ ----------
test('админ меняет каталог, пользователь — нет', async () => {
  await assertSucceeds(addDoc(collection(as(ADMIN), 'categories'), { name: 'Fillers' }));
  await assertFails(addDoc(collection(as(USER), 'categories'), { name: 'Hack' }));
  await assertFails(setDoc(doc(as(USER), 'products', 'p1'), { name: 'x' }));
  await assertFails(setDoc(doc(as(USER), 'product_images', 'p1'), { data: 'x' }));
  await assertFails(addDoc(collection(as(USER), 'routes'), { name: 'x' }));
  await assertFails(setDoc(doc(as(USER), 'achievements', '77'), { title: 'x', experienceReward: 1 }));
  await assertSucceeds(getDoc(doc(as(USER), 'categories', 'c1')));
});

test('админ выдаёт достижения и меняет опыт/роль', async () => {
  const db = as(ADMIN);
  const b = writeBatch(db);
  b.set(doc(db, 'users', USER, 'achievements', '99'), { earned: true, achievementId: '99' });
  b.update(doc(db, 'users', USER), { experience: 1000, level: 3 });
  await assertSucceeds(b.commit());
  await assertSucceeds(updateDoc(doc(db, 'users', USER), { role: 'ADMIN' }));
  await assertSucceeds(deleteDoc(doc(db, 'users', USER, 'achievements', '99')));
});

test('пользователь не может удалить чужое или своё достижение', async () => {
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), 'users', USER, 'achievements', '99'), { earned: true }));
  await assertFails(deleteDoc(doc(as(USER), 'users', USER, 'achievements', '99')));
});

// ---------- Маршруты ----------
const item = (o = {}) => ({
  routeId: 'r1', productId: 'p1', productName: 'Пакет', brandName: '', categoryName: '', subcategoryName: '',
  cellNumber: 'A1', quantity: '2', reminderMinutes: 15, completed: false, addedAt: Date.now(), ...o,
});

test('сборщик добавляет товар в маршрут, отмечает и удаляет', async () => {
  const db = as(USER);
  await assertSucceeds(setDoc(doc(db, 'route_items', 'i1'), item()));
  await assertSucceeds(updateDoc(doc(db, 'route_items', 'i1'), { completed: true }));
  await assertFails(updateDoc(doc(db, 'route_items', 'i1'), { productName: 'другое' }));
  await assertSucceeds(deleteDoc(doc(db, 'route_items', 'i1')));
});

test('нельзя добавить товар в несуществующий маршрут', async () => {
  await assertFails(setDoc(doc(as(USER), 'route_items', 'i2'), item({ routeId: 'nope' })));
});

test('неизвестные коллекции закрыты', async () => {
  await assertFails(setDoc(doc(as(ADMIN), 'secret', 'x'), { a: 1 }));
});

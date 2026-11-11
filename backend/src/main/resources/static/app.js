'use strict';

/*
 * Web-клиент КР3 для проверки REST API. Без фреймворков: fetch + DOM.
 * Access JWT хранится только в памяти вкладки, refresh-токен - в HttpOnly cookie (JS его не видит).
 * Все права проверяет сервер; скрытие кнопок здесь - только удобство.
 */
const state = {
    accessToken: null,
    user: null,
    refreshPromise: null,
    rooms: [],
    guestsForSelect: [],
    bookings: {page: 0, size: 10, sort: 'checkIn,desc', query: '', data: null},
    selected: new Set(),
    guests: {page: 0, query: '', data: null},
    users: {page: 0, query: '', data: null},
    audit: {page: 0, query: '', sort: 'occurred,desc', data: null},
    notesBookingId: null,
    editingNote: null
};

const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>'"]/g, ch => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'}[ch]));
const roles = () => new Set(state.user?.roles || []);
const isAdmin = () => roles().has('ADMIN');
const canEdit = () => roles().has('MANAGER') || isAdmin();
const empty = value => { const s = String(value ?? '').trim(); return s || null; };

// ---------------------------------------------------------------- подписи

const STATUS = {CREATED: 'Создано', CONFIRMED: 'Подтверждено', COMPLETED: 'Завершено', CANCELLED: 'Отменено'};
const RISK = {NOT_APPLICABLE: 'Не считается', LOW: 'Низкий', MEDIUM: 'Средний', HIGH: 'Высокий', CRITICAL: 'Критический'};
const ROOM_TYPE = {STANDARD: 'Стандарт', DELUXE: 'Делюкс', SUITE: 'Люкс'};
const ROLE = {VIEWER: 'Просмотр', MANAGER: 'Менеджер', ADMIN: 'Администратор'};
const TRANSITIONS = {CREATED: ['CONFIRMED', 'CANCELLED'], CONFIRMED: ['COMPLETED', 'CANCELLED'], COMPLETED: [], CANCELLED: []};
const FIELD = {
    code: 'код', guestId: 'гость', roomId: 'номер', checkIn: 'заезд', checkOut: 'выезд', guestsCount: 'гостей',
    status: 'статус', specialRequests: 'пожелания', tags: 'теги', fullName: 'ФИО', phone: 'телефон', email: 'email',
    roomNumber: 'номер', roomType: 'тип', capacity: 'вместимость', pricePerNight: 'цена', displayName: 'имя',
    roles: 'роли', body: 'текст', newPassword: 'новый пароль', version: 'версия'
};

const money = v => v == null ? '—' : Number(v).toLocaleString('ru-RU', {minimumFractionDigits: 0, maximumFractionDigits: 2}) + ' ₽';
const date = v => v ? new Date(v + 'T00:00:00').toLocaleDateString('ru-RU') : '—';
const dateTime = v => v ? new Intl.DateTimeFormat('ru-RU', {dateStyle: 'short', timeStyle: 'short'}).format(new Date(v)) : '—';
const badge = (cls, text) => `<span class="badge ${cls}">${esc(text)}</span>`;
const options = (map, selected, keys = Object.keys(map)) =>
    keys.map(k => `<option value="${k}" ${k === selected ? 'selected' : ''}>${esc(map[k])}</option>`).join('');

function toast(message, error = false) {
    const node = $('toast');
    node.textContent = message;
    node.className = `toast show${error ? ' error' : ''}`;
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => node.className = 'toast', 4000);
}

// ---------------------------------------------------------------- HTTP

async function parseError(response) {
    try {
        const body = await response.json();
        const fields = Object.entries(body.validationErrors || {}).map(([k, v]) => `${FIELD[k] || k}: ${v}`).join('; ');
        return `${body.message || response.statusText}${fields ? ' — ' + fields : ''}`;
    } catch (_) {
        return `${response.status} ${response.statusText}`;
    }
}

/** Запрос к API. При 401 один раз пробует обновить access JWT по refresh-cookie и повторяет запрос. */
async function api(path, options = {}, retry = true) {
    const headers = new Headers(options.headers || {});
    if (state.accessToken) headers.set('Authorization', `Bearer ${state.accessToken}`);
    if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
    const response = await fetch(`/api${path}`, {...options, headers, credentials: 'same-origin'});
    if (response.status === 401 && retry && await refreshAccessToken()) return api(path, options, false);
    if (response.status === 401) { showLogin(); throw new Error('Сессия истекла, войдите снова'); }
    if (!response.ok) throw new Error(await parseError(response));
    if (response.status === 204) return null;
    return (response.headers.get('content-type') || '').includes('application/json') ? response.json() : response;
}

/** Обновление access JWT. Параллельные запросы ждут один и тот же refresh. */
function refreshAccessToken() {
    if (!state.refreshPromise) {
        state.refreshPromise = fetch('/api/auth/refresh', {method: 'POST', credentials: 'same-origin'})
            .then(async response => {
                if (!response.ok) return false;
                const result = await response.json();
                state.accessToken = result.accessToken;
                state.user = result.user;
                return true;
            })
            .catch(() => false)
            .finally(() => state.refreshPromise = null);
    }
    return state.refreshPromise;
}

// ---------------------------------------------------------------- вход / выход

async function boot() {
    bindEvents();
    if (await refreshAccessToken()) await enterApplication(); else showLogin();
}

function showLogin() {
    $('app-view').hidden = true;
    $('login-view').hidden = false;
    state.accessToken = null;
    state.user = null;
}

async function login(event) {
    event.preventDefault();
    const button = event.submitter || $('login-form').querySelector('button');
    $('login-error').hidden = true;
    button.disabled = true;
    try {
        const response = await fetch('/api/auth/login', {
            method: 'POST', credentials: 'same-origin', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({email: $('login-email').value, password: $('login-password').value, clientType: 'WEB'})
        });
        if (!response.ok) throw new Error(await parseError(response));
        const result = await response.json();
        state.accessToken = result.accessToken;
        state.user = result.user;
        $('login-password').value = '';
        await enterApplication();
    } catch (error) {
        showLogin();
        $('login-error').textContent = error.message;
        $('login-error').hidden = false;
    } finally {
        button.disabled = false;
    }
}

async function enterApplication() {
    $('login-view').hidden = true;
    $('app-view').hidden = false;
    renderCurrentUser();
    if (state.user.passwordChangeRequired) {
        toast('Задайте постоянный пароль вместо временного', true);
        if (!await openPasswordDialog(true)) return;
    }
    applyRoleVisibility();
    switchTab('dashboard');
    loadRooms();
}

function renderCurrentUser() {
    $('current-user').innerHTML = `<strong>${esc(state.user.displayName)}</strong><br>${esc(state.user.email)}<br>`
        + state.user.roles.map(r => esc(ROLE[r] || r)).join(', ');
    applyRoleVisibility();
}

function applyRoleVisibility() {
    document.querySelectorAll('.admin-only').forEach(node => node.hidden = !isAdmin());
    document.querySelectorAll('.editor-only').forEach(node => node.hidden = !canEdit());
}

async function logout() {
    try { await fetch('/api/auth/logout', {method: 'POST', credentials: 'same-origin'}); } catch (_) { /* всё равно выходим */ }
    showLogin();
}

function openPasswordDialog(required) {
    return new Promise(resolve => {
        const dialog = $('entity-dialog');
        const form = $('entity-form');
        form.innerHTML = `<div class="dialog-head"><h3>${required ? 'Постоянный пароль' : 'Смена пароля'}</h3>
            ${required ? '' : '<button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button>'}</div>
            ${required ? '<p class="muted">Вы вошли с временным паролем. Пока он не заменён, остальные функции недоступны.</p>'
                : '<label>Текущий пароль<input name="current" type="password" required maxlength="128" autocomplete="current-password"></label>'}
            <label>Новый пароль<input name="next" type="password" required minlength="12" maxlength="128" autocomplete="new-password"></label>
            <label>Повторите новый пароль<input name="confirm" type="password" required minlength="12" maxlength="128" autocomplete="new-password"></label>
            <p class="hint">12+ символов: заглавная и строчная буква, цифра, спецсимвол; без имени учётной записи.</p>
            <div class="form-message" data-form-error hidden></div>
            <button class="primary" type="submit">Сохранить пароль</button>`;
        let done = false;
        const onClose = () => { dialog.removeEventListener('close', onClose); if (!done) resolve(false); };
        dialog.addEventListener('close', onClose);
        dialog.oncancel = required ? (e => e.preventDefault()) : null;
        form.onsubmit = async event => {
            event.preventDefault();
            const data = new FormData(form);
            if (data.get('next') !== data.get('confirm')) return formError(form, 'Пароли не совпадают');
            try {
                if (required) {
                    await api('/auth/change-initial-password', {method: 'POST', body: JSON.stringify({newPassword: data.get('next')})});
                    state.user.passwordChangeRequired = false;
                    await refreshAccessToken(); // новая cookie для Swagger у администратора
                    done = true; dialog.close(); toast('Пароль сохранён'); resolve(true);
                } else {
                    await api('/auth/change-password', {method: 'POST', body: JSON.stringify({currentPassword: data.get('current'), newPassword: data.get('next')})});
                    done = true; dialog.close(); toast('Пароль изменён. Войдите с новым паролем'); resolve(true);
                    await logout();
                }
            } catch (error) { formError(form, error.message); }
        };
        dialog.showModal();
    });
}

function formError(form, message) {
    const box = form.querySelector('[data-form-error]');
    if (!box) return toast(message, true);
    box.textContent = message;
    box.hidden = false;
}

// ---------------------------------------------------------------- вкладки

const TITLES = {
    dashboard: ['Аналитика', 'Сводные показатели по бронированиям'],
    bookings: ['Бронирования', 'Поиск, фильтры, сортировка, архив, массовые операции, CSV'],
    guests: ['Гости', 'Справочник гостей гостиницы'],
    rooms: ['Номера', 'Справочник номеров (изменяет администратор)'],
    users: ['Пользователи', 'Учётные записи, роли, временные пароли'],
    audit: ['Журнал аудита', 'Входы и изменения данных']
};

function switchTab(name) {
    document.querySelectorAll('.tab').forEach(tab => tab.classList.toggle('active', tab.id === `tab-${name}`));
    document.querySelectorAll('.nav-item[data-tab]').forEach(b => b.classList.toggle('active', b.dataset.tab === name));
    [$('page-title').textContent, $('page-subtitle').textContent] = TITLES[name];
    ({dashboard: loadDashboard, bookings: loadBookings, guests: loadGuests, rooms: loadRooms, users: loadUsers, audit: loadAudit})[name]();
}

function pager(prefix, data, unit) {
    $(`${prefix}-page`).textContent = `Стр. ${data.page + 1} из ${Math.max(1, data.totalPages)} · ${unit}: ${data.totalElements}`;
    $(`${prefix}-prev`).disabled = data.first;
    $(`${prefix}-next`).disabled = data.last;
}

// ---------------------------------------------------------------- аналитика

async function loadDashboard() {
    try {
        const d = await api('/analytics/summary');
        const metrics = [
            ['Всего броней', d.total], ['Активные', d.active], ['В архиве', d.archived], ['С предоплатой', d.prepaid],
            ['Без предоплаты', d.notPrepaid], ['Средняя сумма', money(d.averageTotalPrice)],
            ['Выручка (завершённые)', money(d.completedRevenue)], ['Номеров в работе', d.byRoom.length]
        ];
        $('metric-grid').innerHTML = metrics.map(([n, v]) => `<article class="metric card"><span class="muted">${n}</span><strong>${esc(v)}</strong></article>`).join('');
        bars('chart-rooms', d.byRoom, n => `№ ${n}`);
        bars('chart-types', d.byRoomType, n => ROOM_TYPE[n] || n);
        bars('chart-status', d.byStatus, n => STATUS[n] || n);
        bars('chart-risk', d.byRisk, n => RISK[n] || n);
    } catch (error) { toast(error.message, true); }
}

function bars(id, items, label) {
    const max = Math.max(1, ...items.map(i => i.count));
    $(id).innerHTML = items.length ? items.map(i => `<div class="bar-row"><span>${esc(label(i.name))}</span>
        <div class="bar-track"><div class="bar-fill" style="width:${(i.count / max * 100).toFixed(1)}%"></div></div>
        <strong>${i.count}</strong></div>`).join('') : '<p class="muted">Нет данных</p>';
}

// ---------------------------------------------------------------- бронирования

async function loadBookings(page = state.bookings.page) {
    const b = state.bookings;
    try {
        b.data = await api(`/bookings?q=${encodeURIComponent(b.query)}&page=${page}&size=${b.size}&sort=${encodeURIComponent(b.sort)}`);
        b.page = b.data.page;
        state.selected.clear();
        renderBookings();
    } catch (error) { toast(error.message, true); }
}

function renderBookings() {
    const rows = state.bookings.data.content.map(x => `<tr class="${x.archived ? 'archived' : ''}">
        <td class="editor-only"><input type="checkbox" data-select="${x.id}"></td>
        <td class="nowrap"><strong>${esc(x.code)}</strong>${x.archived ? '<br><small>в архиве</small>' : ''}</td>
        <td>${esc(x.guestName)}<br><small>${esc(x.guestEmail)}</small></td>
        <td class="nowrap">№ ${x.roomNumber}<br><small>${esc(ROOM_TYPE[x.roomType])}</small></td>
        <td class="nowrap">${date(x.checkIn)} – ${date(x.checkOut)}<br><small>ночей: ${x.nights}</small></td>
        <td class="num">${x.guestsCount}</td>
        <td>${badge('status-' + x.status, STATUS[x.status])}</td>
        <td class="num">${money(x.totalPrice)}</td>
        <td>${x.prepaid ? badge('success', 'Да') : 'Нет'}</td>
        <td>${badge('risk-' + x.riskLevel, RISK[x.riskLevel])}</td>
        <td>${x.tags.map(t => badge('', t)).join('') || '—'}</td>
        <td><div class="actions">
            <button class="secondary" data-view="${x.id}">Карточка</button>
            <button class="secondary" data-notes="${x.id}">Заметки</button>
            ${canEdit() && !x.archived ? `<button class="secondary" data-edit="${x.id}">Изменить</button>` : ''}
            ${canEdit() ? `<button class="danger-outline" data-archive="${x.id}">${x.archived ? 'Восстановить' : 'В архив'}</button>` : ''}
            ${isAdmin() && x.archived ? `<button class="danger-outline" data-delete="${x.id}">Удалить</button>` : ''}
        </div></td></tr>`).join('');
    $('bookings-body').innerHTML = rows || '<tr><td colspan="12" class="muted">Ничего не найдено</td></tr>';
    $('select-all').checked = false;
    pager('bookings', state.bookings.data, 'броней');
    applyRoleVisibility();
}

const findBooking = id => state.bookings.data.content.find(x => x.id === id);

async function ensureReferences() {
    const [rooms, guests] = await Promise.all([api('/rooms'), api('/guests?size=100&sort=name,asc')]);
    state.rooms = rooms;
    state.guestsForSelect = guests.content;
}

async function openBooking(id = null, readOnly = false) {
    try {
        const [booking] = await Promise.all([id ? api(`/bookings/${id}`) : null, ensureReferences()]);
        const form = $('entity-form');
        form.innerHTML = readOnly ? bookingCard(booking) : bookingForm(booking);
        if (!readOnly) bindBookingForm(form, booking);
        $('entity-dialog').showModal();
    } catch (error) { toast(error.message, true); }
}

function bookingCard(x) {
    const row = (k, v) => `<dt>${k}</dt><dd>${v}</dd>`;
    return `<div class="dialog-head"><h3>Бронь ${esc(x.code)}</h3><button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button></div>
        <dl class="card-view">
        ${row('Гость', `${esc(x.guestName)}<br><small>${esc(x.guestEmail)} · ${esc(x.guestPhone)}</small>`)}
        ${row('Номер', `№ ${x.roomNumber} (${esc(ROOM_TYPE[x.roomType])})`)}
        ${row('Даты', `${date(x.checkIn)} – ${date(x.checkOut)}, ночей: ${x.nights}`)}
        ${row('Гостей', x.guestsCount)}
        ${row('Статус', badge('status-' + x.status, STATUS[x.status]))}
        ${row('Сумма', money(x.totalPrice))}
        ${row('Предоплата', x.prepaid ? 'внесена' : 'нет')}
        ${row('Риск отмены', badge('risk-' + x.riskLevel, RISK[x.riskLevel]))}
        ${row('Пожелания', esc(x.specialRequests || '—'))}
        ${row('Теги', x.tags.map(t => badge('', t)).join('') || '—')}
        ${row('Архив', x.archived ? 'да, с ' + dateTime(x.archivedAt) : 'нет')}
        ${row('Создана / изменена', `${dateTime(x.createdAt)} / ${dateTime(x.updatedAt)}`)}
        ${row('UUID / version', `<small>${esc(x.id)}</small> / ${x.version}`)}
        </dl>`;
}

function bookingForm(x) {
    const guests = state.guestsForSelect.map(g => `<option value="${g.id}" ${g.id === x?.guestId ? 'selected' : ''}>${esc(g.fullName)} — ${esc(g.email)}</option>`).join('');
    const rooms = state.rooms.filter(r => r.active || r.id === x?.roomId).map(r =>
        `<option value="${r.id}" data-price="${r.pricePerNight}" data-capacity="${r.capacity}" ${r.id === x?.roomId ? 'selected' : ''}>
         № ${r.roomNumber} · ${esc(ROOM_TYPE[r.roomType])} · до ${r.capacity} гостей · ${money(r.pricePerNight)}/ночь</option>`).join('');
    const statusKeys = x ? [x.status, ...TRANSITIONS[x.status]] : ['CREATED', 'CONFIRMED'];
    const locked = x && !['CREATED', 'CONFIRMED'].includes(x.status) ? 'disabled' : '';
    return `<div class="dialog-head"><h3>${x ? 'Изменение брони ' + esc(x.code) : 'Новая бронь'}</h3><button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button></div>
    <div class="form-grid">
      <label class="span-2">Гость<select name="guestId" required ${locked}><option value="">— выберите —</option>${guests}</select></label>
      <label class="span-2">Номер<select name="roomId" required ${locked}><option value="">— выберите —</option>${rooms}</select></label>
      <label>Заезд<input name="checkIn" type="date" required value="${x?.checkIn || ''}" ${locked}></label>
      <label>Выезд<input name="checkOut" type="date" required value="${x?.checkOut || ''}" ${locked}></label>
      <label>Гостей<input name="guestsCount" type="number" min="1" max="6" required value="${x?.guestsCount ?? 1}" ${locked}></label>
      <label>Статус<select name="status">${options(STATUS, x?.status || 'CREATED', statusKeys)}</select></label>
      <label>Код брони <span class="hint">(пусто — выдаст сервер)</span><input name="code" maxlength="20" pattern="[A-Za-z0-9\\-]{3,20}" value="${esc(x?.code || '')}"></label>
      <label class="inline"><input name="prepaid" type="checkbox" ${x?.prepaid ? 'checked' : ''}> Предоплата внесена</label>
      <label class="span-2">Пожелания гостя<textarea name="specialRequests" maxlength="1000">${esc(x?.specialRequests || '')}</textarea></label>
      <label class="span-2">Теги через запятую<input name="tags" maxlength="500" value="${esc((x?.tags || []).join(', '))}"></label>
    </div>
    <p class="hint" id="price-preview"></p>
    <div class="form-message" data-form-error hidden></div>
    <button class="primary" type="submit">Сохранить</button>`;
}

function bindBookingForm(form, x) {
    const preview = () => {
        const room = form.elements.roomId.selectedOptions[0];
        const nights = (new Date(form.elements.checkOut.value) - new Date(form.elements.checkIn.value)) / 86400000;
        $('price-preview').textContent = room?.dataset.price && nights > 0
            ? `Предварительно: ${nights} ноч. × ${money(room.dataset.price)} = ${money(nights * room.dataset.price)} (итог и риск считает сервер)` : '';
    };
    ['roomId', 'checkIn', 'checkOut'].forEach(n => form.elements[n].addEventListener('change', preview));
    preview();
    form.onsubmit = async event => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        const d = new FormData(form);
        const value = name => form.elements[name].disabled ? x[name] : d.get(name); // disabled поля не попадают в FormData
        const payload = {
            code: empty(d.get('code')), guestId: value('guestId'), roomId: value('roomId'),
            checkIn: value('checkIn'), checkOut: value('checkOut'), guestsCount: Number(value('guestsCount')),
            status: d.get('status'), prepaid: d.get('prepaid') === 'on', specialRequests: empty(d.get('specialRequests')),
            tags: String(d.get('tags') || '').split(',').map(t => t.trim()).filter(Boolean), version: x?.version ?? null
        };
        try {
            await api(x ? `/bookings/${x.id}` : '/bookings', {method: x ? 'PUT' : 'POST', body: JSON.stringify(payload)});
            $('entity-dialog').close();
            toast(x ? 'Бронь изменена' : 'Бронь создана');
            loadBookings();
        } catch (error) { formError(form, error.message); }
    };
}

async function toggleArchive(id) {
    const x = findBooking(id);
    if (!confirm(`${x.archived ? 'Восстановить' : 'Переместить в архив'} бронь ${x.code}?`)) return;
    try {
        await api(x.archived ? `/bookings/${id}/restore` : `/bookings/${id}`, {method: x.archived ? 'POST' : 'DELETE'});
        toast(x.archived ? 'Бронь восстановлена' : 'Бронь перемещена в архив');
        loadBookings();
    } catch (error) { toast(error.message, true); }
}

async function deleteForever(id) {
    const x = findBooking(id);
    const code = prompt(`Бронь будет удалена без возможности восстановления.\nВведите её код (${x.code}) для подтверждения:`);
    if (!code) return;
    try {
        await api(`/bookings/${id}/permanent`, {method: 'DELETE', headers: {'X-Confirm-Delete': code}});
        toast('Бронь удалена окончательно');
        loadBookings();
    } catch (error) { toast(error.message, true); }
}

// ---------------------------------------------------------------- массовые операции

function openBulk() {
    if (!state.selected.size) return toast('Отметьте брони галочками', true);
    $('bulk-count').textContent = `Выбрано броней: ${state.selected.size}`;
    $('bulk-result').innerHTML = '';
    updateBulkFields();
    $('bulk-dialog').showModal();
}

function updateBulkFields() {
    const type = $('bulk-type').value;
    $('bulk-status-wrap').hidden = type !== 'SET_STATUS';
    $('bulk-tag-wrap').hidden = !type.endsWith('_TAG');
}

async function runBulk(event) {
    event.preventDefault();
    const action = $('bulk-type').value;
    const payload = {ids: [...state.selected], action};
    if (action === 'SET_STATUS') payload.status = $('bulk-status').value;
    if (action.endsWith('_TAG')) payload.tag = $('bulk-tag').value;
    try {
        const r = await api('/bookings/bulk', {method: 'POST', body: JSON.stringify(payload)});
        $('bulk-result').innerHTML = `<div class="result-box"><strong>Успешно: ${r.succeeded} из ${r.requested}</strong>
            ${r.failures.length ? `<table><thead><tr><th>Бронь</th><th>Ошибка</th></tr></thead><tbody>${r.failures.map(f =>
                `<tr><td>${esc(f.code || f.id)}</td><td>${esc(f.message)}</td></tr>`).join('')}</tbody></table>` : ''}</div>`;
        loadBookings();
    } catch (error) { toast(error.message, true); }
}

// ---------------------------------------------------------------- CSV

async function exportCsv() {
    try {
        const response = await api(`/bookings/export?q=${encodeURIComponent(state.bookings.query)}`);
        const url = URL.createObjectURL(await response.blob());
        const link = document.createElement('a');
        link.href = url;
        link.download = `bookings-${new Date().toISOString().slice(0, 10)}.csv`;
        link.click();
        URL.revokeObjectURL(url);
    } catch (error) { toast(error.message, true); }
}

async function runCsv(apply) {
    const file = $('csv-file').files[0];
    if (!file) return toast('Выберите CSV-файл', true);
    const form = new FormData();
    form.append('file', file);
    try {
        const r = await api(`/bookings/import/${apply ? 'apply' : 'preview'}?mode=${$('csv-mode').value}`, {method: 'POST', body: form});
        const summary = apply
            ? `Строк: ${r.totalRows} · создано: ${r.created} · обновлено: ${r.updated} · пропущено: ${r.skipped}`
            : `Строк: ${r.totalRows} · корректных: ${r.validRows} (новых ${r.toCreate}, обновлений ${r.toUpdate}) · с ошибками: ${r.invalidRows}`;
        $('csv-result').innerHTML = `<div class="result-box"><strong>${summary}</strong>${r.issues.length
            ? `<table><thead><tr><th>Строка</th><th>Поле</th><th>Ошибка</th></tr></thead><tbody>${r.issues.map(i =>
                `<tr><td>${i.row}</td><td>${esc(i.field)}</td><td>${esc(i.message)}</td></tr>`).join('')}</tbody></table>` : ''}</div>`;
        $('csv-apply').disabled = apply || r.validRows === 0;
        if (apply) { toast('Импорт выполнен'); loadBookings(); }
    } catch (error) {
        $('csv-result').innerHTML = `<div class="form-message">${esc(error.message)}</div>`;
        $('csv-apply').disabled = true;
    }
}

// ---------------------------------------------------------------- заметки

async function openNotes(bookingId) {
    state.notesBookingId = bookingId;
    const x = findBooking(bookingId);
    $('notes-title').textContent = `Заметки · ${x?.code || ''}`;
    resetNoteForm();
    await renderNotes();
    $('notes-dialog').showModal();
}

async function renderNotes() {
    try {
        const list = await api(`/bookings/${state.notesBookingId}/notes`);
        state.notes = list;
        $('notes-list').innerHTML = list.map(n => {
            const mine = n.authorId === state.user.id || isAdmin();
            return `<article class="note ${n.pinned ? 'pinned' : ''}"><div class="note-meta">
                <span>${esc(n.authorName)} · ${dateTime(n.createdAt)}${n.updatedAt !== n.createdAt ? ' (изм. ' + dateTime(n.updatedAt) + ')' : ''}</span>
                <span>${n.pinned ? '📌 закреплена' : ''}</span></div><p>${esc(n.body)}</p>
                ${canEdit() && mine ? `<div class="actions"><button class="secondary" data-note-edit="${n.id}">Изменить</button>
                <button class="danger-outline" data-note-delete="${n.id}">Удалить</button></div>` : ''}</article>`;
        }).join('') || '<p class="muted">Заметок пока нет</p>';
    } catch (error) { toast(error.message, true); }
}

function resetNoteForm() {
    state.editingNote = null;
    $('note-body').value = '';
    $('note-pinned').checked = false;
    $('note-submit').textContent = 'Добавить заметку';
    $('note-cancel').hidden = true;
}

async function submitNote(event) {
    event.preventDefault();
    const n = state.editingNote;
    const body = JSON.stringify({body: $('note-body').value, pinned: $('note-pinned').checked, version: n?.version ?? null});
    try {
        await api(`/bookings/${state.notesBookingId}/notes${n ? '/' + n.id : ''}`, {method: n ? 'PUT' : 'POST', body});
        resetNoteForm();
        await renderNotes();
    } catch (error) { toast(error.message, true); }
}

async function notesClick(event) {
    const edit = event.target.closest('[data-note-edit]');
    const del = event.target.closest('[data-note-delete]');
    if (edit) {
        const n = state.notes.find(x => x.id === edit.dataset.noteEdit);
        state.editingNote = n;
        $('note-body').value = n.body;
        $('note-pinned').checked = n.pinned;
        $('note-submit').textContent = 'Сохранить изменения';
        $('note-cancel').hidden = false;
        $('note-body').focus();
    }
    if (del && confirm('Удалить заметку?')) {
        try {
            await api(`/bookings/${state.notesBookingId}/notes/${del.dataset.noteDelete}`, {method: 'DELETE'});
            await renderNotes();
        } catch (error) { toast(error.message, true); }
    }
}

// ---------------------------------------------------------------- гости

async function loadGuests(page = state.guests.page) {
    try {
        const d = await api(`/guests?q=${encodeURIComponent(state.guests.query)}&page=${page}&size=20&sort=name,asc`);
        state.guests.data = d;
        state.guests.page = d.page;
        $('guests-body').innerHTML = d.content.map(g => `<tr><td><strong>${esc(g.fullName)}</strong></td><td>${esc(g.phone)}</td>
            <td>${esc(g.email)}</td><td>${dateTime(g.createdAt)}</td>
            <td class="editor-only"><button class="secondary" data-guest-edit="${g.id}">Изменить</button></td></tr>`).join('')
            || '<tr><td colspan="5" class="muted">Никого не найдено</td></tr>';
        pager('guests', d, 'гостей');
        applyRoleVisibility();
    } catch (error) { toast(error.message, true); }
}

function openGuest(id = null) {
    const g = id ? state.guests.data.content.find(x => x.id === id) : null;
    const form = $('entity-form');
    form.innerHTML = `<div class="dialog-head"><h3>${g ? 'Изменение гостя' : 'Новый гость'}</h3><button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button></div>
        <label>ФИО<input name="fullName" required minlength="3" maxlength="100" value="${esc(g?.fullName || '')}"></label>
        <label>Телефон<input name="phone" required maxlength="20" placeholder="+7 900 123-45-67" value="${esc(g?.phone || '')}"></label>
        <label>Email<input name="email" type="email" required maxlength="100" value="${esc(g?.email || '')}"></label>
        <div class="form-message" data-form-error hidden></div>
        <button class="primary" type="submit">Сохранить</button>`;
    form.onsubmit = async event => {
        event.preventDefault();
        const d = new FormData(form);
        const payload = {fullName: d.get('fullName'), phone: d.get('phone'), email: d.get('email'), version: g?.version ?? null};
        try {
            await api(g ? `/guests/${g.id}` : '/guests', {method: g ? 'PUT' : 'POST', body: JSON.stringify(payload)});
            $('entity-dialog').close();
            toast('Гость сохранён');
            loadGuests();
        } catch (error) { formError(form, error.message); }
    };
    $('entity-dialog').showModal();
}

// ---------------------------------------------------------------- номера

async function loadRooms() {
    try {
        state.rooms = await api('/rooms');
        $('rooms-body').innerHTML = state.rooms.map(r => `<tr><td><strong>№ ${r.roomNumber}</strong></td><td>${esc(ROOM_TYPE[r.roomType])}</td>
            <td class="num">${r.capacity}</td><td class="num">${money(r.pricePerNight)}</td>
            <td>${r.active ? badge('success', 'Да') : badge('', 'Отключён')}</td>
            <td class="admin-only"><div class="actions"><button class="secondary" data-room-edit="${r.id}">Изменить</button>
            <button class="secondary" data-room-toggle="${r.id}">${r.active ? 'Отключить' : 'Включить'}</button></div></td></tr>`).join('');
        applyRoleVisibility();
    } catch (error) { toast(error.message, true); }
}

function openRoom(id = null) {
    const r = id ? state.rooms.find(x => x.id === id) : null;
    const form = $('entity-form');
    form.innerHTML = `<div class="dialog-head"><h3>${r ? 'Изменение номера' : 'Новый номер'}</h3><button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button></div>
        <div class="form-grid">
        <label>Номер комнаты<input name="roomNumber" type="number" min="1" max="9999" required value="${r?.roomNumber ?? ''}"></label>
        <label>Тип<select name="roomType">${options(ROOM_TYPE, r?.roomType || 'STANDARD')}</select></label>
        <label>Вместимость<input name="capacity" type="number" min="1" max="6" required value="${r?.capacity ?? 2}"></label>
        <label>Цена за ночь, ₽<input name="pricePerNight" type="number" min="1" step="0.01" required value="${r?.pricePerNight ?? ''}"></label>
        <label class="inline"><input name="active" type="checkbox" ${r?.active !== false ? 'checked' : ''}> Активен</label>
        </div>
        <p class="hint">Новая цена применяется к новым броням и к броням, у которых меняются номер или даты.</p>
        <div class="form-message" data-form-error hidden></div>
        <button class="primary" type="submit">Сохранить</button>`;
    form.onsubmit = async event => {
        event.preventDefault();
        const d = new FormData(form);
        const payload = {roomNumber: Number(d.get('roomNumber')), roomType: d.get('roomType'), capacity: Number(d.get('capacity')),
            pricePerNight: Number(d.get('pricePerNight')), active: d.get('active') === 'on', version: r?.version ?? null};
        try {
            await api(r ? `/rooms/${r.id}` : '/rooms', {method: r ? 'PUT' : 'POST', body: JSON.stringify(payload)});
            $('entity-dialog').close();
            toast('Номер сохранён');
            loadRooms();
        } catch (error) { formError(form, error.message); }
    };
    $('entity-dialog').showModal();
}

async function toggleRoom(id) {
    const r = state.rooms.find(x => x.id === id);
    try {
        await api(`/rooms/${id}/active?active=${!r.active}&version=${r.version}`, {method: 'PATCH'});
        loadRooms();
    } catch (error) { toast(error.message, true); }
}

// ---------------------------------------------------------------- пользователи

async function loadUsers(page = state.users.page) {
    try {
        const d = await api(`/admin/users?q=${encodeURIComponent(state.users.query)}&page=${page}&size=20&sort=email,asc`);
        state.users.data = d;
        state.users.page = d.page;
        $('users-body').innerHTML = d.content.map(u => `<tr><td>${esc(u.email)}</td><td>${esc(u.displayName)}</td>
            <td>${u.roles.map(r => badge('', ROLE[r] || r)).join('')}</td><td>${u.enabled ? 'Да' : badge('', 'Отключён')}</td>
            <td>${u.passwordChangeRequired ? badge('warning', 'Да') : 'Нет'}</td>
            <td>${u.lockedUntil && new Date(u.lockedUntil) > new Date() ? 'до ' + dateTime(u.lockedUntil) : '—'}</td>
            <td><div class="actions"><button class="secondary" data-user-edit="${u.id}">Изменить</button>
            <button class="secondary" data-user-reset="${u.id}">Сбросить пароль</button></div></td></tr>`).join('');
        pager('users', d, 'пользователей');
    } catch (error) { toast(error.message, true); }
}

function openUser(id = null) {
    const u = id ? state.users.data.content.find(x => x.id === id) : null;
    const form = $('entity-form');
    const roleBoxes = Object.keys(ROLE).map(r => `<label class="inline"><input type="checkbox" name="roles" value="${r}"
        ${(u ? u.roles.includes(r) : r === 'VIEWER') ? 'checked' : ''}> ${ROLE[r]}</label>`).join('');
    form.innerHTML = `<div class="dialog-head"><h3>${u ? 'Изменение пользователя' : 'Новый пользователь'}</h3><button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button></div>
        <label>Email (логин)<input name="email" type="email" required maxlength="254" value="${esc(u?.email || '')}"></label>
        <label>Имя<input name="displayName" required maxlength="160" value="${esc(u?.displayName || '')}"></label>
        <fieldset><legend>Роли</legend>${roleBoxes}</fieldset>
        ${u ? `<label class="inline"><input type="checkbox" name="enabled" ${u.enabled ? 'checked' : ''}> Учётная запись активна</label>`
            : '<p class="hint">Временный пароль сгенерирует сервер и покажет один раз.</p>'}
        <div class="form-message" data-form-error hidden></div>
        <button class="primary" type="submit">Сохранить</button>`;
    form.onsubmit = async event => {
        event.preventDefault();
        const d = new FormData(form);
        const payload = {email: d.get('email'), displayName: d.get('displayName'), roles: d.getAll('roles')};
        if (u) Object.assign(payload, {enabled: d.get('enabled') === 'on', version: u.version});
        try {
            const result = await api(u ? `/admin/users/${u.id}` : '/admin/users', {method: u ? 'PUT' : 'POST', body: JSON.stringify(payload)});
            if (u) { $('entity-dialog').close(); toast('Пользователь сохранён'); } else showTemporaryPassword(result);
            loadUsers();
        } catch (error) { formError(form, error.message); }
    };
    $('entity-dialog').showModal();
}

function showTemporaryPassword(result) {
    $('entity-form').innerHTML = `<div class="dialog-head"><h3>Временный пароль</h3><button type="button" data-close-dialog="entity-dialog" class="icon-button">×</button></div>
        <p>Пользователь <strong>${esc(result.user.email)}</strong>. Передайте пароль сотруднику — при первом входе его нужно сменить.
        Больше он нигде не показывается (в базе хранится только BCrypt-хеш).</p>
        <div class="secret">${esc(result.temporaryPassword)}</div>
        <button type="button" class="primary" data-close-dialog="entity-dialog">Готово</button>`;
    $('entity-form').onsubmit = null;
    if (!$('entity-dialog').open) $('entity-dialog').showModal();
}

async function resetPassword(id) {
    const u = state.users.data.content.find(x => x.id === id);
    if (!confirm(`Сбросить пароль ${u.email}? Все его сессии будут закрыты.`)) return;
    try {
        showTemporaryPassword(await api(`/admin/users/${id}/reset-password`, {method: 'POST'}));
        loadUsers();
    } catch (error) { toast(error.message, true); }
}

// ---------------------------------------------------------------- аудит

async function loadAudit(page = state.audit.page) {
    const a = state.audit;
    try {
        const d = await api(`/admin/audit?q=${encodeURIComponent(a.query)}&page=${page}&size=25&sort=${a.sort}`);
        a.data = d;
        a.page = d.page;
        $('audit-body').innerHTML = d.content.map(e => `<tr><td>${dateTime(e.occurredAt)}</td><td>${esc(e.actorEmail || 'system')}</td>
            <td>${badge('', e.eventType)}</td><td>${esc(e.entityType || '—')}</td><td>${esc(e.summary)}</td>
            <td><small>${esc(e.correlationId || '—')}</small></td></tr>`).join('') || '<tr><td colspan="6" class="muted">Нет записей</td></tr>';
        pager('audit', d, 'записей');
    } catch (error) { toast(error.message, true); }
}

// ---------------------------------------------------------------- события

function bindEvents() {
    $('login-form').addEventListener('submit', login);
    $('logout').addEventListener('click', logout);
    $('change-password').addEventListener('click', () => openPasswordDialog(false));
    $('copy-token').addEventListener('click', async () => {
        try { await navigator.clipboard.writeText(state.accessToken); toast('JWT скопирован: Swagger → Authorize → вставить'); }
        catch (_) { toast('Не удалось скопировать', true); }
    });
    $('navigation').addEventListener('click', e => { const b = e.target.closest('[data-tab]'); if (b) switchTab(b.dataset.tab); });
    document.body.addEventListener('click', e => { const c = e.target.closest('[data-close-dialog]'); if (c) $(c.dataset.closeDialog).close(); });

    // бронирования
    const searchBookings = () => { state.bookings.query = $('booking-search').value.trim(); loadBookings(0); };
    $('search-bookings').addEventListener('click', searchBookings);
    $('booking-search').addEventListener('keydown', e => { if (e.key === 'Enter') searchBookings(); });
    $('booking-sort').addEventListener('change', e => { state.bookings.sort = e.target.value; loadBookings(0); });
    $('booking-size').addEventListener('change', e => { state.bookings.size = Number(e.target.value); loadBookings(0); });
    $('bookings-prev').addEventListener('click', () => loadBookings(state.bookings.page - 1));
    $('bookings-next').addEventListener('click', () => loadBookings(state.bookings.page + 1));
    $('add-booking').addEventListener('click', () => openBooking());
    $('bulk-action').addEventListener('click', openBulk);
    $('bulk-type').addEventListener('change', updateBulkFields);
    $('bulk-form').addEventListener('submit', runBulk);
    $('export-csv').addEventListener('click', exportCsv);
    $('import-csv').addEventListener('click', () => { $('csv-result').innerHTML = ''; $('csv-apply').disabled = true; $('csv-dialog').showModal(); });
    $('csv-file').addEventListener('change', () => { $('csv-apply').disabled = true; $('csv-result').innerHTML = ''; });
    $('csv-preview').addEventListener('click', () => runCsv(false));
    $('csv-apply').addEventListener('click', () => runCsv(true));
    $('bookings-body').addEventListener('change', e => {
        const box = e.target.closest('[data-select]');
        if (box) box.checked ? state.selected.add(box.dataset.select) : state.selected.delete(box.dataset.select);
    });
    $('select-all').addEventListener('change', e => document.querySelectorAll('[data-select]').forEach(box => {
        box.checked = e.target.checked;
        e.target.checked ? state.selected.add(box.dataset.select) : state.selected.delete(box.dataset.select);
    }));
    $('bookings-body').addEventListener('click', e => {
        const t = e.target.closest('button');
        if (!t) return;
        if (t.dataset.view) openBooking(t.dataset.view, true);
        if (t.dataset.edit) openBooking(t.dataset.edit, false);
        if (t.dataset.notes) openNotes(t.dataset.notes);
        if (t.dataset.archive) toggleArchive(t.dataset.archive);
        if (t.dataset.delete) deleteForever(t.dataset.delete);
    });

    // заметки
    $('note-form').addEventListener('submit', submitNote);
    $('note-cancel').addEventListener('click', resetNoteForm);
    $('notes-list').addEventListener('click', notesClick);

    // гости
    const searchGuests = () => { state.guests.query = $('guest-search').value.trim(); loadGuests(0); };
    $('search-guests').addEventListener('click', searchGuests);
    $('guest-search').addEventListener('keydown', e => { if (e.key === 'Enter') searchGuests(); });
    $('guests-prev').addEventListener('click', () => loadGuests(state.guests.page - 1));
    $('guests-next').addEventListener('click', () => loadGuests(state.guests.page + 1));
    $('add-guest').addEventListener('click', () => openGuest());
    $('guests-body').addEventListener('click', e => { const b = e.target.closest('[data-guest-edit]'); if (b) openGuest(b.dataset.guestEdit); });

    // номера
    $('add-room').addEventListener('click', () => openRoom());
    $('rooms-body').addEventListener('click', e => {
        const edit = e.target.closest('[data-room-edit]');
        const toggle = e.target.closest('[data-room-toggle]');
        if (edit) openRoom(edit.dataset.roomEdit);
        if (toggle) toggleRoom(toggle.dataset.roomToggle);
    });

    // пользователи
    const searchUsers = () => { state.users.query = $('user-search').value.trim(); loadUsers(0); };
    $('search-users').addEventListener('click', searchUsers);
    $('user-search').addEventListener('keydown', e => { if (e.key === 'Enter') searchUsers(); });
    $('users-prev').addEventListener('click', () => loadUsers(state.users.page - 1));
    $('users-next').addEventListener('click', () => loadUsers(state.users.page + 1));
    $('add-user').addEventListener('click', () => openUser());
    $('users-body').addEventListener('click', e => {
        const edit = e.target.closest('[data-user-edit]');
        const reset = e.target.closest('[data-user-reset]');
        if (edit) openUser(edit.dataset.userEdit);
        if (reset) resetPassword(reset.dataset.userReset);
    });

    // аудит
    const searchAudit = () => { state.audit.query = $('audit-search').value.trim(); loadAudit(0); };
    $('search-audit').addEventListener('click', searchAudit);
    $('audit-search').addEventListener('keydown', e => { if (e.key === 'Enter') searchAudit(); });
    $('audit-sort').addEventListener('change', e => { state.audit.sort = e.target.value; loadAudit(0); });
    $('audit-prev').addEventListener('click', () => loadAudit(state.audit.page - 1));
    $('audit-next').addEventListener('click', () => loadAudit(state.audit.page + 1));
}

boot().catch(error => { showLogin(); toast(error.message || 'Ошибка запуска', true); });

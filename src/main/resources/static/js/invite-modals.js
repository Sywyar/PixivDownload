/* global window, document */
/**
 * 邀请码相关的共享 modal：创建/编辑邀请、配置可见标签或作者、展示邀请链接结果。
 * 由 pixiv-gallery.html、pixiv-invite-manage.html、pixiv-invite-detail.html 复用。
 */
(function () {
    'use strict';

    const STYLE_ID = 'invite-modals-style';
    if (!document.getElementById(STYLE_ID)) {
        const style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = `
        .invite-modal-backdrop {
            position: fixed; inset: 0; background: rgba(0,0,0,0.5);
            display: none; align-items: center; justify-content: center;
            z-index: 9000; padding: 16px;
        }
        .invite-modal-backdrop.open { display: flex; }
        .invite-modal {
            background: var(--surface, #fff); color: var(--text, #222);
            border-radius: 12px; width: 100%; max-width: 560px;
            max-height: calc(100vh - 32px); display: flex; flex-direction: column;
            box-shadow: 0 12px 48px rgba(0,0,0,0.25);
        }
        .invite-modal.large { max-width: 760px; }
        .invite-modal-head {
            display: flex; align-items: center; justify-content: space-between;
            padding: 16px 20px; border-bottom: 1px solid var(--border, #eee);
            font-size: 16px; font-weight: 600;
        }
        .invite-modal-close {
            background: none; border: none; cursor: pointer; font-size: 20px;
            color: var(--text-muted, #888); line-height: 1;
        }
        .invite-modal-body { padding: 20px; overflow: auto; flex: 1; }
        .invite-modal-foot {
            padding: 12px 20px; border-top: 1px solid var(--border, #eee);
            display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap;
        }
        .invite-field { margin-bottom: 14px; }
        .invite-field-label { display: block; font-size: 12px; font-weight: 600; margin-bottom: 6px; color: var(--text-muted, #555); }
        .invite-input {
            width: 100%; padding: 8px 10px; border: 1px solid var(--border, #ddd);
            border-radius: 6px; font-size: 13px; background: var(--surface, #fff); color: inherit;
        }
        .invite-input:focus { outline: none; border-color: var(--brand, #28a745); }
        .invite-row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
        .invite-chip {
            display: inline-flex; align-items: center; padding: 4px 10px;
            border-radius: 999px; border: 1px solid var(--border, #ddd);
            background: var(--surface, #fff); color: inherit;
            cursor: pointer; font-size: 12px; user-select: none;
        }
        .invite-chip.active {
            background: var(--brand, #28a745); color: #fff; border-color: var(--brand, #28a745);
        }
        .invite-checkbox-row { display: flex; gap: 14px; flex-wrap: wrap; align-items: center; }
        .invite-btn {
            padding: 8px 14px; border-radius: 6px; border: 1px solid var(--border, #ddd);
            background: var(--surface, #fff); color: inherit; cursor: pointer; font-size: 13px;
        }
        .invite-btn.primary { background: var(--brand, #28a745); color: #fff; border-color: var(--brand, #28a745); }
        .invite-btn.danger { background: #dc3545; color: #fff; border-color: #dc3545; }
        .invite-btn:disabled { opacity: 0.55; cursor: not-allowed; }
        .invite-error { font-size: 12px; color: #dc3545; min-height: 16px; margin-top: 8px; }
        .invite-help { font-size: 12px; color: var(--text-muted, #777); margin-top: 4px; }
        .invite-link-block {
            display: flex; gap: 8px; align-items: center; padding: 8px 10px;
            border: 1px solid var(--border, #ddd); border-radius: 6px;
            background: var(--surface-muted, #f6f6f6); margin-bottom: 8px;
            word-break: break-all; font-family: ui-monospace, Consolas, monospace; font-size: 12px;
        }
        .invite-link-block .invite-link-value { flex: 1; }

        /* 标签/作者配置弹窗 */
        .invite-picker-toolbar {
            display: flex; gap: 8px; align-items: center; margin-bottom: 10px; flex-wrap: wrap;
        }
        .invite-picker-list {
            border: 1px solid var(--border, #eee); border-radius: 6px;
            max-height: 50vh; min-height: 240px; overflow-y: auto; background: var(--surface, #fff);
        }
        .invite-picker-row {
            display: flex; align-items: center; justify-content: space-between;
            padding: 8px 12px; border-bottom: 1px solid var(--border, #f0f0f0);
            cursor: pointer; gap: 12px;
        }
        .invite-picker-row:last-child { border-bottom: none; }
        .invite-picker-row:hover { background: var(--surface-hover, #fafafa); }
        .invite-picker-name { flex: 1; font-size: 13px; word-break: break-all; }
        .invite-picker-name .secondary { color: var(--text-muted, #888); margin-left: 6px; font-size: 12px; }
        .invite-picker-toggle {
            min-width: 60px; padding: 4px 10px; border-radius: 999px;
            font-size: 11px; font-weight: 600; text-align: center; cursor: pointer;
            border: 1px solid transparent;
        }
        .invite-picker-toggle.visible { background: #e6f6e9; color: #1a7d33; border-color: #b9e1c2; }
        .invite-picker-toggle.hidden { background: #fbe8e8; color: #b41a1a; border-color: #efb2b2; }
        .invite-picker-meta { font-size: 12px; color: var(--text-muted, #777); margin-bottom: 8px; }
        .invite-picker-empty { padding: 24px; text-align: center; color: var(--text-muted, #888); font-size: 13px; }
        `;
        document.head.appendChild(style);
    }

    function el(tag, attrs, children) {
        const node = document.createElement(tag);
        if (attrs) {
            for (const [k, v] of Object.entries(attrs)) {
                if (v == null) continue;
                if (k === 'class') node.className = v;
                else if (k === 'style' && typeof v === 'object') Object.assign(node.style, v);
                else if (k === 'text') node.textContent = v;
                else if (k.startsWith('on') && typeof v === 'function') {
                    node.addEventListener(k.slice(2).toLowerCase(), v);
                } else if (k === 'value') node.value = v;
                else if (k === 'checked') node.checked = !!v;
                else if (k === 'disabled') node.disabled = !!v;
                else node.setAttribute(k, v);
            }
        }
        if (children) {
            for (const child of children) {
                if (child == null) continue;
                node.appendChild(typeof child === 'string' ? document.createTextNode(child) : child);
            }
        }
        return node;
    }

    function closeBackdrop(backdrop) { if (backdrop && backdrop.parentNode) backdrop.parentNode.removeChild(backdrop); }

    async function copyText(text) {
        try {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                await navigator.clipboard.writeText(text); return true;
            }
        } catch (_) { /* fallthrough */ }
        try {
            const ta = document.createElement('textarea');
            ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
            document.body.appendChild(ta); ta.select();
            const ok = document.execCommand('copy'); document.body.removeChild(ta); return ok;
        } catch (_) { return false; }
    }

    /**
     * 打开"配置可见标签 / 配置可见作者"弹窗。
     *
     * @param {Object} opts
     * @param {'tag'|'author'} opts.kind
     * @param {Array} opts.items 列表项 [{id, name, secondary?}]
     * @param {boolean} opts.unrestricted 当前是否不限制（即"全部可见"）
     * @param {Set<number>} opts.selectedIds 当前可见 ID 集合（unrestricted=false 时生效）
     * @param {Function} opts.onSubmit ({unrestricted, ids}) => void
     */
    function openVisibilityPicker(opts) {
        const { kind, items, onSubmit } = opts;
        let unrestricted = !!opts.unrestricted;
        let selected = new Set(opts.selectedIds || []);
        let filter = ''; // ''=all, 'visible', 'hidden'
        let keyword = '';

        const backdrop = el('div', { class: 'invite-modal-backdrop open' });
        const titleI18n = kind === 'tag' ? '配置可见标签' : '配置可见作者';
        const list = el('div', { class: 'invite-picker-list' });

        function isVisible(item) {
            if (unrestricted) return true;
            return selected.has(item.id);
        }
        function rowMatchesFilter(item) {
            if (filter === 'visible' && !isVisible(item)) return false;
            if (filter === 'hidden' && isVisible(item)) return false;
            if (keyword) {
                const k = keyword.toLowerCase();
                const name = (item.name || '').toLowerCase();
                const sec = (item.secondary || '').toLowerCase();
                if (!name.includes(k) && !sec.includes(k)) return false;
            }
            return true;
        }
        function renderList() {
            list.innerHTML = '';
            const filtered = items.filter(rowMatchesFilter);
            if (filtered.length === 0) {
                list.appendChild(el('div', { class: 'invite-picker-empty', text: '暂无匹配项' }));
                return;
            }
            for (const item of filtered) {
                const visible = isVisible(item);
                const toggle = el('span', {
                    class: 'invite-picker-toggle ' + (visible ? 'visible' : 'hidden'),
                    text: visible ? '可见' : '不可见'
                });
                const nameNode = el('div', { class: 'invite-picker-name' }, [
                    item.name || '',
                    item.secondary ? el('span', { class: 'secondary', text: item.secondary }) : null
                ]);
                const row = el('div', { class: 'invite-picker-row' }, [nameNode, toggle]);
                row.addEventListener('click', () => {
                    if (unrestricted) {
                        // 切换为受限模式，并保留当前所有可见项作为初始集合
                        unrestricted = false;
                        selected = new Set(items.map(it => it.id));
                        unrestrictedChip.classList.remove('active');
                    }
                    if (selected.has(item.id)) selected.delete(item.id);
                    else selected.add(item.id);
                    renderList();
                });
                list.appendChild(row);
            }
        }

        const search = el('input', {
            class: 'invite-input', placeholder: '搜索', style: { flex: '1', minWidth: '160px' },
            oninput: (e) => { keyword = e.target.value || ''; renderList(); }
        });
        const filterAll = el('button', { type: 'button', class: 'invite-chip active', text: '全部',
            onclick: () => { filter = ''; setActive(filterAll); renderList(); } });
        const filterVisible = el('button', { type: 'button', class: 'invite-chip', text: '可见',
            style: { background: '#e6f6e9', color: '#1a7d33', borderColor: '#b9e1c2' },
            onclick: () => { filter = 'visible'; setActive(filterVisible); renderList(); } });
        const filterHidden = el('button', { type: 'button', class: 'invite-chip', text: '不可见',
            style: { background: '#fbe8e8', color: '#b41a1a', borderColor: '#efb2b2' },
            onclick: () => { filter = 'hidden'; setActive(filterHidden); renderList(); } });
        function setActive(chip) {
            [filterAll, filterVisible, filterHidden].forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
        }
        const unrestrictedChip = el('button', {
            type: 'button', class: 'invite-chip' + (unrestricted ? ' active' : ''),
            text: '全部可见',
            onclick: () => {
                unrestricted = !unrestricted;
                if (unrestricted) {
                    unrestrictedChip.classList.add('active');
                    selected.clear();
                } else {
                    unrestrictedChip.classList.remove('active');
                }
                renderList();
            }
        });

        const toolbar = el('div', { class: 'invite-picker-toolbar' }, [
            search, filterAll, filterVisible, filterHidden, unrestrictedChip
        ]);
        const meta = el('div', { class: 'invite-picker-meta',
            text: `共 ${items.length} 项；选择"全部可见"则该维度不限制。` });

        const cancelBtn = el('button', { type: 'button', class: 'invite-btn', text: '取消',
            onclick: () => closeBackdrop(backdrop) });
        const okBtn = el('button', { type: 'button', class: 'invite-btn primary', text: '保存',
            onclick: () => {
                onSubmit({ unrestricted, ids: Array.from(selected) });
                closeBackdrop(backdrop);
            } });

        const modal = el('div', { class: 'invite-modal large' }, [
            el('div', { class: 'invite-modal-head' }, [
                el('span', { text: titleI18n }),
                el('button', { class: 'invite-modal-close', text: '×', onclick: () => closeBackdrop(backdrop) })
            ]),
            el('div', { class: 'invite-modal-body' }, [meta, toolbar, list]),
            el('div', { class: 'invite-modal-foot' }, [cancelBtn, okBtn])
        ]);
        backdrop.appendChild(modal);
        document.body.appendChild(backdrop);
        renderList();
    }

    /**
     * 打开"创建/编辑邀请"弹窗。
     *
     * @param {Object} opts
     * @param {Object} [opts.prefill] 初始值，编辑时传入；否则为新建
     * @param {string} opts.title 弹窗标题
     * @param {string} opts.submitText 提交按钮文案
     * @param {Function} opts.onSubmit 异步：(payload) => Promise<{code, url}> （新建）或任意（编辑）
     * @param {Function} opts.fetchTags 异步：() => Array<{id, name, secondary?}>
     * @param {Function} opts.fetchAuthors 异步：() => Array<{id, name, secondary?}>
     */
    function openInviteFormModal(opts) {
        const prefill = opts.prefill || {};
        let allowSfw = prefill.allowSfw !== false;
        let allowR18 = !!prefill.allowR18;
        let allowR18g = !!prefill.allowR18g;
        let tagUnrestricted = prefill.tagUnrestricted !== false;
        let authorUnrestricted = prefill.authorUnrestricted !== false;
        let tagIds = new Set(prefill.tagIds || []);
        let authorIds = new Set(prefill.authorIds || []);

        const nameInput = el('input', { class: 'invite-input', value: prefill.name || '',
            placeholder: '访客名称（用于记忆）' });
        const expireInput = el('input', { class: 'invite-input', type: 'number', min: '1',
            value: prefill.expireDays != null ? String(prefill.expireDays) : '7',
            placeholder: '天数', style: { width: '120px' } });
        function presetChip(label, days) {
            return el('button', { type: 'button', class: 'invite-chip', text: label,
                onclick: () => {
                    if (days == null) {
                        expireInput.value = '';
                        expireInput.disabled = true;
                        expireInput.dataset.permanent = '1';
                    } else {
                        expireInput.disabled = false;
                        delete expireInput.dataset.permanent;
                        expireInput.value = String(days);
                    }
                } });
        }
        const expireRow = el('div', { class: 'invite-row' }, [
            expireInput,
            el('span', { text: '天' }),
            presetChip('7天', 7), presetChip('30天', 30), presetChip('365天', 365),
            presetChip('永久', null)
        ]);
        if (prefill.expireDays == null && prefill.permanent) {
            expireInput.value = '';
            expireInput.disabled = true;
            expireInput.dataset.permanent = '1';
        }

        function makeCheckbox(label, checked, onChange) {
            const cb = el('input', { type: 'checkbox', checked });
            cb.addEventListener('change', () => onChange(cb.checked));
            return el('label', { class: 'invite-row', style: { gap: '6px', cursor: 'pointer' } },
                [cb, el('span', { text: label })]);
        }
        const ageRow = el('div', { class: 'invite-checkbox-row' }, [
            makeCheckbox('SFW', allowSfw, v => allowSfw = v),
            makeCheckbox('R18', allowR18, v => allowR18 = v),
            makeCheckbox('R18G', allowR18g, v => allowR18g = v)
        ]);

        const tagPickerBtn = el('button', { type: 'button', class: 'invite-btn', text: '配置可见标签',
            onclick: async () => {
                tagPickerBtn.disabled = true;
                try {
                    const items = await opts.fetchTags();
                    openVisibilityPicker({
                        kind: 'tag', items,
                        unrestricted: tagUnrestricted,
                        selectedIds: tagIds,
                        onSubmit: ({ unrestricted, ids }) => {
                            tagUnrestricted = unrestricted;
                            tagIds = new Set(ids);
                            updateSummary();
                        }
                    });
                } finally { tagPickerBtn.disabled = false; }
            } });
        const authorPickerBtn = el('button', { type: 'button', class: 'invite-btn', text: '配置可见作者',
            onclick: async () => {
                authorPickerBtn.disabled = true;
                try {
                    const items = await opts.fetchAuthors();
                    openVisibilityPicker({
                        kind: 'author', items,
                        unrestricted: authorUnrestricted,
                        selectedIds: authorIds,
                        onSubmit: ({ unrestricted, ids }) => {
                            authorUnrestricted = unrestricted;
                            authorIds = new Set(ids);
                            updateSummary();
                        }
                    });
                } finally { authorPickerBtn.disabled = false; }
            } });

        const summary = el('div', { class: 'invite-help' });
        function updateSummary() {
            const parts = [];
            parts.push('标签: ' + (tagUnrestricted ? '全部' : `${tagIds.size} 个`));
            parts.push('作者: ' + (authorUnrestricted ? '全部' : `${authorIds.size} 个`));
            summary.textContent = parts.join(' · ') + '（OR 语义：标签命中或作者命中即可见）';
        }
        updateSummary();

        const errorBox = el('div', { class: 'invite-error' });
        const submitBtn = el('button', { type: 'button', class: 'invite-btn primary',
            text: opts.submitText || '创建邀请链接' });

        const backdrop = el('div', { class: 'invite-modal-backdrop open' });
        submitBtn.addEventListener('click', async () => {
            errorBox.textContent = '';
            const name = nameInput.value.trim();
            if (!name) { errorBox.textContent = '请填写访客名称'; return; }
            let expireDays = null;
            if (!expireInput.dataset.permanent) {
                const raw = expireInput.value.trim();
                if (!raw) { errorBox.textContent = '请填写有效期或选择"永久"'; return; }
                expireDays = parseInt(raw, 10);
                if (!(expireDays > 0)) { errorBox.textContent = '有效期必须为正整数'; return; }
            }
            if (!allowSfw && !allowR18 && !allowR18g) {
                errorBox.textContent = '请至少选择一个年龄分级'; return;
            }
            if (!tagUnrestricted && tagIds.size === 0
                && !authorUnrestricted && authorIds.size === 0) {
                errorBox.textContent = '请至少配置一项可见标签或可见作者，或将其中一项设为"全部可见"';
                return;
            }
            const payload = {
                name, expireDays,
                allowSfw, allowR18, allowR18g,
                tagUnrestricted, tagIds: tagUnrestricted ? [] : Array.from(tagIds),
                authorUnrestricted, authorIds: authorUnrestricted ? [] : Array.from(authorIds)
            };
            submitBtn.disabled = true;
            try {
                await opts.onSubmit(payload);
                closeBackdrop(backdrop);
            } catch (e) {
                errorBox.textContent = e && e.message ? e.message : '提交失败';
            } finally {
                submitBtn.disabled = false;
            }
        });
        const cancelBtn = el('button', { type: 'button', class: 'invite-btn', text: '取消',
            onclick: () => closeBackdrop(backdrop) });

        const body = el('div', { class: 'invite-modal-body' }, [
            el('div', { class: 'invite-field' }, [
                el('label', { class: 'invite-field-label', text: '访客名称' }),
                nameInput,
                el('div', { class: 'invite-help', text: '仅用于你自己记忆，访客看不到。' })
            ]),
            el('div', { class: 'invite-field' }, [
                el('label', { class: 'invite-field-label', text: '有效期' }),
                expireRow
            ]),
            el('div', { class: 'invite-field' }, [
                el('label', { class: 'invite-field-label', text: '可见年龄分级（可多选）' }),
                ageRow
            ]),
            el('div', { class: 'invite-field' }, [
                el('label', { class: 'invite-field-label', text: '可见范围' }),
                el('div', { class: 'invite-row' }, [tagPickerBtn, authorPickerBtn]),
                summary
            ]),
            errorBox
        ]);

        const modal = el('div', { class: 'invite-modal' }, [
            el('div', { class: 'invite-modal-head' }, [
                el('span', { text: opts.title || '邀请访客' }),
                el('button', { class: 'invite-modal-close', text: '×',
                    onclick: () => closeBackdrop(backdrop) })
            ]),
            body,
            el('div', { class: 'invite-modal-foot' }, [cancelBtn, submitBtn])
        ]);
        backdrop.appendChild(modal);
        document.body.appendChild(backdrop);
    }

    function openInviteResultModal(result) {
        const backdrop = el('div', { class: 'invite-modal-backdrop open' });
        function copyButton(text) {
            return el('button', { type: 'button', class: 'invite-btn', text: '复制',
                onclick: async (e) => {
                    const ok = await copyText(text);
                    e.target.textContent = ok ? '已复制' : '复制失败';
                    setTimeout(() => { e.target.textContent = '复制'; }, 1500);
                } });
        }
        const modal = el('div', { class: 'invite-modal' }, [
            el('div', { class: 'invite-modal-head' }, [
                el('span', { text: '邀请链接已生成' }),
                el('button', { class: 'invite-modal-close', text: '×',
                    onclick: () => closeBackdrop(backdrop) })
            ]),
            el('div', { class: 'invite-modal-body' }, [
                el('div', { class: 'invite-field-label', text: '邀请链接' }),
                el('div', { class: 'invite-link-block' }, [
                    el('span', { class: 'invite-link-value', text: result.url }),
                    copyButton(result.url)
                ]),
                el('div', { class: 'invite-field-label', text: '邀请码' }),
                el('div', { class: 'invite-link-block' }, [
                    el('span', { class: 'invite-link-value', text: result.code }),
                    copyButton(result.code)
                ]),
                el('div', { class: 'invite-help',
                    text: '通过链接访问可自动登录；也可在登录页粘贴邀请码进入。' })
            ]),
            el('div', { class: 'invite-modal-foot' }, [
                el('button', { type: 'button', class: 'invite-btn primary', text: '确定',
                    onclick: () => closeBackdrop(backdrop) })
            ])
        ]);
        backdrop.appendChild(modal);
        document.body.appendChild(backdrop);
    }

    window.InviteModals = {
        openInviteFormModal,
        openInviteResultModal,
        openVisibilityPicker,
        copyText,
    };
})();

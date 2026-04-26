(function (global) {
    'use strict';

    var STORAGE_KEY = 'pixiv.lang';
    var DEFAULT_NAMESPACE = 'common';

    function normalizeLang(lang) {
        if (!lang) {
            return 'en-US';
        }
        return String(lang).trim().replace('_', '-');
    }

    function readStoredLang() {
        try {
            return global.localStorage.getItem(STORAGE_KEY);
        } catch (e) {
            return null;
        }
    }

    function writeStoredLang(lang) {
        try {
            global.localStorage.setItem(STORAGE_KEY, lang);
        } catch (e) {
            // Ignore storage failures.
        }
    }

    async function fetchJson(url) {
        var response = await global.fetch(url, { credentials: 'same-origin' });
        var payload = null;
        try {
            payload = await response.json();
        } catch (e) {
            payload = null;
        }
        if (!response.ok) {
            var message = payload && payload.error ? payload.error : response.statusText;
            throw new Error(message || 'Request failed');
        }
        return payload || {};
    }

    function resolveKey(namespaces, key) {
        if (!key) {
            return { namespace: namespaces[0], key: '' };
        }
        var index = key.indexOf(':');
        if (index < 0) {
            return { namespace: namespaces[0], key: key };
        }
        return {
            namespace: key.slice(0, index),
            key: key.slice(index + 1)
        };
    }

    function interpolate(template, vars) {
        if (!vars) {
            return template;
        }
        return String(template).replace(/\{([a-zA-Z0-9_.-]+)\}/g, function (match, name) {
            return Object.prototype.hasOwnProperty.call(vars, name) ? vars[name] : match;
        });
    }

    function translate(client, key, fallback, vars) {
        var resolved = resolveKey(client.namespaces, key);
        var namespaceMessages = client.bundleMap[resolved.namespace] || {};
        var template = namespaceMessages[resolved.key];
        if (template == null) {
            template = fallback != null ? fallback : key;
        }
        return interpolate(template, vars);
    }

    function findElements(root, selector) {
        var list = [];
        if (root.matches && root.matches(selector)) {
            list.push(root);
        }
        return list.concat(Array.prototype.slice.call(root.querySelectorAll(selector)));
    }

    function parseArgsAttribute(element) {
        var raw = element.getAttribute('data-i18n-args');
        if (!raw) {
            return null;
        }
        try {
            return JSON.parse(raw);
        } catch (e) {
            return null;
        }
    }

    function applyBindings(root, client) {
        findElements(root, '[data-i18n]').forEach(function (element) {
            element.textContent = translate(client, element.getAttribute('data-i18n'), element.textContent, parseArgsAttribute(element));
        });

        findElements(root, '[data-i18n-html]').forEach(function (element) {
            element.innerHTML = translate(client, element.getAttribute('data-i18n-html'), element.innerHTML, parseArgsAttribute(element));
        });

        findElements(root, '[data-i18n-placeholder]').forEach(function (element) {
            element.setAttribute(
                'placeholder',
                translate(client, element.getAttribute('data-i18n-placeholder'), element.getAttribute('placeholder'), parseArgsAttribute(element))
            );
        });

        findElements(root, '[data-i18n-title]').forEach(function (element) {
            element.setAttribute(
                'title',
                translate(client, element.getAttribute('data-i18n-title'), element.getAttribute('title'), parseArgsAttribute(element))
            );
        });
    }

    function buildClient(meta, namespaces, bundleMap) {
        var client = {
            lang: meta.currentLang,
            defaultLang: meta.defaultLang,
            namespaces: namespaces.slice(),
            supportedLocales: (meta.supportedLocales || []).slice(),
            bundleMap: bundleMap,
            t: function (key, fallback, vars) {
                return translate(client, key, fallback, vars);
            },
            has: function (key) {
                var resolved = resolveKey(client.namespaces, key);
                var namespaceMessages = client.bundleMap[resolved.namespace] || {};
                return Object.prototype.hasOwnProperty.call(namespaceMessages, resolved.key);
            },
            apply: function (root) {
                applyBindings(root || global.document, client);
                return client;
            },
            setLanguage: function (lang) {
                return create({
                    lang: lang,
                    namespaces: namespaces.slice()
                });
            }
        };
        return client;
    }

    async function create(options) {
        var config = options || {};
        var namespaces = Array.isArray(config.namespaces) && config.namespaces.length
            ? config.namespaces.slice()
            : [DEFAULT_NAMESPACE];
        var preferredLang = normalizeLang(config.lang || readStoredLang() || global.navigator.language);
        var meta = await fetchJson('/api/i18n/meta?lang=' + encodeURIComponent(preferredLang));
        var bundleMap = {};

        for (var i = 0; i < namespaces.length; i += 1) {
            var namespace = namespaces[i];
            var bundle = await fetchJson('/api/i18n/messages/' + encodeURIComponent(namespace) + '?lang=' + encodeURIComponent(meta.currentLang));
            bundleMap[namespace] = bundle.messages || {};
        }

        writeStoredLang(meta.currentLang);
        if (global.document && global.document.documentElement) {
            global.document.documentElement.lang = meta.currentLang;
        }

        return buildClient(meta, namespaces, bundleMap);
    }

    global.PixivI18n = {
        create: create,
        normalizeLang: normalizeLang,
        storageKey: STORAGE_KEY
    };
})(window);

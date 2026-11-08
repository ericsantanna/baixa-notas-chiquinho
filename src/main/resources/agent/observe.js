// Tags visible clickable elements with data-agent-id and returns a JSON snapshot of the page.
// arguments[0] = max number of elements to return.
var MAX = arguments[0];
var norm = function (s) { return (s || '').replace(/\s+/g, ' ').trim(); };
var visible = function (el) {
    if (!el.getClientRects().length) { return false; }
    var cs = getComputedStyle(el);
    return cs.visibility !== 'hidden' && cs.display !== 'none';
};
var firstLine = function (s) {
    var lines = (s || '').split('\n');
    for (var i = 0; i < lines.length; i++) { if (lines[i].trim()) { return norm(lines[i]); } }
    return '';
};

var old = document.querySelectorAll('[data-agent-id]');
for (var i = 0; i < old.length; i++) { old[i].removeAttribute('data-agent-id'); }

var out = [];
var total = 0;
var add = function (el, text, hint) {
    total++;
    if (out.length >= MAX) { return; }
    var id = out.length + 1;
    el.setAttribute('data-agent-id', String(id));
    out.push({
        id: id,
        tag: el.tagName.toLowerCase(),
        text: text.substring(0, 80),
        hint: hint || '',
        href: el.getAttribute('href') || '',
        enabled: !(el.disabled || el.getAttribute('aria-disabled') === 'true' ||
            /ui-state-disabled/.test(String(el.className || '')))
    });
};

// 1) The franchise switcher: ITFGestor's header widget (2nd div.widgets-item) and the entries of its list.
//    Listed explicitly, with the same selectors the scripted flow uses, because its click handler is not
//    guaranteed to be an onclick attribute (the generic scan below would then never see it).
var widgets = document.querySelectorAll('#layout-header div.widgets-item');
var widget = widgets.length > 1 ? widgets[1] : null;
var header = widget ? firstLine(widget.innerText) : '';
if (widget && visible(widget)) {
    add(widget, header || norm(widget.textContent),
        'WIDGET DA FRANQUEADORA ATUAL: clique para abrir/fechar a lista de franqueadoras');
}
var items = document.querySelectorAll('div.lista-empresas li');
for (var a = 0; a < items.length; a++) {
    var link = items[a].querySelector('a'); // 1st link = select franchise, 2nd = favourite star
    if (!link || !visible(link) || link.hasAttribute('data-agent-id')) { continue; }
    var name = norm(link.textContent) || norm(items[a].textContent);
    if (name) { add(link, name, 'item da lista de franqueadoras (clicar seleciona a franqueadora)'); }
}

// 2) Everything else that looks clickable.
var nodes = document.querySelectorAll(
    'a, button, input[type=button], input[type=submit], [onclick], [role=button], [role=menuitem], ' +
    '[role=option], li.ui-selectonemenu-item, .ui-menuitem-link');
for (var j = 0; j < nodes.length; j++) {
    var el = nodes[j];
    if (el.hasAttribute('data-agent-id') || !visible(el)) { continue; }
    var text = norm(el.textContent) || norm(el.getAttribute('title')) ||
        norm(el.getAttribute('aria-label')) || norm(el.value);
    if (!text) { continue; }
    add(el, text, '');
}

var msgs = Array.prototype.slice.call(
    document.querySelectorAll('.ui-messages, .ui-growl-message, .ui-message-error, .ui-dialog'))
    .filter(visible)
    .map(function (m) { return norm(m.innerText); })
    .filter(Boolean)
    .join(' | ')
    .substring(0, 300);

var overlay = Array.prototype.slice.call(document.querySelectorAll('div.ui-widget-overlay.ui-dialog-mask'))
    .some(visible);

return JSON.stringify({
    url: location.href,
    title: document.title,
    header: header,
    banner: msgs,
    overlay: overlay,
    total: total,
    elements: out
});

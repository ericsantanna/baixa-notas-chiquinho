// Scrolls the window and every visible scrollable list by arguments[0] pixels. Returns how many lists moved.
var d = arguments[0];
window.scrollBy(0, d);
var all = document.querySelectorAll('div, ul');
var moved = 0;
for (var i = 0; i < all.length; i++) {
    var el = all[i];
    var oy = getComputedStyle(el).overflowY;
    if ((oy === 'auto' || oy === 'scroll') && el.scrollHeight > el.clientHeight + 5 && el.getClientRects().length) {
        el.scrollTop += d;
        moved++;
    }
}
return moved;

// Deterministic goal check: returns [currentFranchiseName, isOnConsultaCupomFiscalPdvScreen].
var h = document.querySelector('#layout-header');
var w = h ? h.querySelectorAll('div.widgets-item')[1] : null;
var lines = w ? (w.innerText || '').split('\n') : [];
var first = '';
for (var i = 0; i < lines.length; i++) {
    if (lines[i].trim()) { first = lines[i].trim(); break; }
}
var spans = document.querySelectorAll('div.headerTitle h4 span');
var onConsulta = false;
for (var j = 0; j < spans.length; j++) {
    if ((spans[j].textContent || '').trim() === 'Consulta Cupom Fiscal PDV') { onConsulta = true; }
}
return [first, onConsulta];

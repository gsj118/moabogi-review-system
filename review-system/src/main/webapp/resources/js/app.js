'use strict';
document.querySelectorAll('nav[aria-label="주요 메뉴"] a').forEach(function(link) {
  var path = new URL(link.href).pathname;
  if (path === location.pathname || (path !== '/' && location.pathname.indexOf(path) === 0)) link.setAttribute('aria-current', 'page');
});
document.querySelectorAll('form[data-confirm]').forEach(function(form) {
  form.addEventListener('submit', function(event) { if (!window.confirm(form.dataset.confirm)) event.preventDefault(); });
});
document.querySelectorAll('form[data-progress]').forEach(function(form) {
  form.addEventListener('submit', function(event) {
    if (form.dataset.submitting === 'true') { event.preventDefault(); return; }
    if (event.defaultPrevented) return;
    form.dataset.submitting = 'true';
    var message = document.createElement('p'); message.className = 'muted submit-progress'; message.setAttribute('role', 'status'); message.textContent = '처리 중입니다. 잠시 기다려 주세요.'; form.appendChild(message);
    setTimeout(function() { form.querySelectorAll('button[type="submit"]').forEach(function(button) { button.disabled = true; }); }, 0);
  });
});
var error = document.querySelector('.notice.error[tabindex]');
var fieldError = document.querySelector('[data-error-for]');
if (error && fieldError) {
  var fieldLink = document.createElement('a');
  fieldLink.className = 'link error-field-link';
  fieldLink.href = '#' + fieldError.dataset.errorFor;
  fieldLink.textContent = '오류 항목으로 이동';
  error.appendChild(fieldLink);
}
if (error) error.focus();

// Use the server's calendar date so shortcuts follow the same day as validation.
document.querySelectorAll('[data-date-offset]').forEach(function(button) {
  button.addEventListener('click', function() {
    var input = document.getElementById('viewedOn');
    if (!input || input.readOnly || !input.max) return;
    var day = new Date(input.max + 'T12:00:00Z');
    day.setUTCDate(day.getUTCDate() + Number(button.dataset.dateOffset));
    input.value = day.toISOString().slice(0, 10);
    input.dispatchEvent(new Event('input', {bubbles: true}));
    input.focus();
  });
});
var reviewInput = document.getElementById('review');
var reviewCount = document.getElementById('review-count');
if (reviewInput && reviewCount) {
  function countReview() { reviewCount.textContent = reviewInput.value.length.toLocaleString('ko-KR') + ' / 4,000자'; }
  reviewInput.addEventListener('input', countReview);
  countReview();
}
var saveEffect = document.querySelector('[data-save-effect]');
if (saveEffect) {
  function explainSave() {
    var action = document.querySelector('input[name="action"]:checked');
    saveEffect.textContent = '저장 후: 새 감상 1건 추가 · ' + (action && action.value === 'COMPLETE'
      ? '현재 상태를 완료로 변경' : '현재 상태 ' + saveEffect.dataset.currentState + ' 유지');
  }
  document.querySelectorAll('input[name="action"]').forEach(function(input) { input.addEventListener('change', explainSave); });
  explainSave();
}

// Announce only tables that actually overflow their own container.
document.querySelectorAll('.table-wrap').forEach(function(wrap, index) {
  var hint = document.createElement('p');
  hint.id = 'table-scroll-hint-' + index;
  hint.className = 'muted small-text table-scroll-hint';
  hint.textContent = '좌우로 넘겨 확인할 수 있습니다. 키보드는 표에 초점을 둔 뒤 방향키를 사용하세요.';
  wrap.parentNode.insertBefore(hint, wrap);
  wrap.setAttribute('role', 'region');
  var caption = wrap.querySelector('caption');
  wrap.setAttribute('aria-label', caption ? caption.textContent : '기록 표');
  function update() {
    var overflow = wrap.scrollWidth > wrap.clientWidth + 1;
    hint.hidden = !overflow;
    if (overflow) { wrap.setAttribute('tabindex', '0'); wrap.setAttribute('aria-describedby', hint.id); }
    else { wrap.removeAttribute('tabindex'); wrap.removeAttribute('aria-describedby'); }
  }
  update();
  window.addEventListener('resize', update);
  if (window.ResizeObserver) new ResizeObserver(update).observe(wrap);
});

// A page restored by Back must allow another intentional submission.
window.addEventListener('pageshow', function(event) {
  if (!event.persisted) return;
  document.querySelectorAll('form[data-progress]').forEach(function(form) {
    delete form.dataset.submitting;
    form.querySelectorAll('button[type="submit"]').forEach(function(button) { button.disabled = false; });
    // Keep permanent status text such as the chosen save effect.
    form.querySelectorAll('.submit-progress').forEach(function(message) { message.remove(); });
  });
});

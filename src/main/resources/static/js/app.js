// State Management
let jwtToken = localStorage.getItem('fred_jwt') || null;
let userProvider = localStorage.getItem('fred_user_provider') || 'LOCAL';
let userName = localStorage.getItem('fred_user_name') || 'Usuário';
let userEmail = localStorage.getItem('fred_user_email') || '';
let currentPortfolioId = null;
let currentAssets = [];
let deferredPrompt = null;

// PWA Service Worker Registration
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js')
      .then(reg => {
        console.log('ServiceWorker registrado:', reg.scope);
        reg.update();
      })
      .catch(err => console.error('Erro ao registrar ServiceWorker:', err));
  });
}

// PWA Install Event Listener
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  deferredPrompt = e;
  const banner = document.getElementById('pwa-install-banner');
  if (banner) banner.classList.remove('hidden');
});

document.getElementById('btn-install-pwa')?.addEventListener('click', async () => {
  if (deferredPrompt) {
    deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    console.log(`Resposta da instalação PWA: ${outcome}`);
    deferredPrompt = null;
    document.getElementById('pwa-install-banner').classList.add('hidden');
  }
});

// App Initialization
document.addEventListener('DOMContentLoaded', () => {
  // Trata redirecionamento de sucesso ou erro do OAuth2
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.has('token')) {
    jwtToken = urlParams.get('token');
    localStorage.setItem('fred_jwt', jwtToken);

    try {
      const payloadBase64 = jwtToken.split('.')[1];
      const payloadJson = decodeURIComponent(atob(payloadBase64).split('').map(c => {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));
      const payload = JSON.parse(payloadJson);
      userEmail = payload.sub || '';
      userName = payload.name || (userEmail.includes('@') ? userEmail.split('@')[0] : 'Usuário');
      userProvider = 'GOOGLE';
      localStorage.setItem('fred_user_provider', userProvider);
      localStorage.setItem('fred_user_email', userEmail);
      localStorage.setItem('fred_user_name', userName);
    } catch (e) {
      userProvider = 'GOOGLE';
      localStorage.setItem('fred_user_provider', userProvider);
      console.warn('Não foi possível extrair dados do token JWT:', e);
    }

    // Limpa a URL da barra de endereços
    window.history.replaceState({}, document.title, window.location.pathname);
    showAppView();
    return;
  }

  if (urlParams.has('error')) {
    const errorMsg = urlParams.get('error');
    window.history.replaceState({}, document.title, window.location.pathname);
    alert('❌ Erro na autenticação OAuth2 com o provedor:\n' + errorMsg);
  }

  if (jwtToken) {
    showAppView();
  } else {
    showAuthView();
  }
});

// Auth Handlers
function showAuthView() {
  document.getElementById('auth-view').classList.remove('hidden');
  document.getElementById('app-view').classList.add('hidden');
  document.getElementById('main-nav').classList.add('hidden');
}

function showAppView() {
  document.getElementById('auth-view').classList.add('hidden');
  document.getElementById('app-view').classList.remove('hidden');
  document.getElementById('main-nav').classList.remove('hidden');

  const nameEl = document.getElementById('user-display-name');
  const providerEl = document.getElementById('user-display-provider');
  if (nameEl) nameEl.innerText = userName || 'Usuário';
  if (providerEl) providerEl.innerText = userProvider || 'LOCAL';

  loadDashboardData();
  loadOpenFinanceInstitutions();
  setupAiProvider();
}

function toggleAuthTab(tab) {
  const loginForm = document.getElementById('form-login');
  const regForm = document.getElementById('form-register');
  const tabLogin = document.getElementById('tab-login');
  const tabReg = document.getElementById('tab-register');

  if (tab === 'login') {
    loginForm.classList.remove('hidden');
    regForm.classList.add('hidden');
    tabLogin.classList.add('active');
    tabReg.classList.remove('active');
  } else {
    loginForm.classList.add('hidden');
    regForm.classList.remove('hidden');
    tabLogin.classList.remove('active');
    tabReg.classList.add('active');
  }
}

async function handleLogin(e) {
  e.preventDefault();
  const email = document.getElementById('login-email').value;
  const password = document.getElementById('login-password').value;

  try {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });

    if (!response.ok) {
      const errData = await response.json().catch(() => ({}));
      throw new Error(errData.message || 'Falha no login. Verifique e-mail e senha.');
    }

    const data = await response.json();
    jwtToken = data.accessToken;
    userProvider = data.provider || 'LOCAL';
    userName = data.fullName || email.split('@')[0];
    userEmail = data.email || email;

    localStorage.setItem('fred_jwt', jwtToken);
    localStorage.setItem('fred_user_provider', userProvider);
    localStorage.setItem('fred_user_name', userName);
    localStorage.setItem('fred_user_email', userEmail);

    showAppView();
  } catch (err) {
    alert(err.message);
  }
}

async function handleRegister(e) {
  e.preventDefault();
  const fullName = document.getElementById('reg-name').value;
  const email = document.getElementById('reg-email').value;
  const password = document.getElementById('reg-password').value;

  try {
    const response = await fetch('/api/v1/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fullName, email, password })
    });

    if (!response.ok) {
      const errData = await response.json().catch(() => ({}));
      throw new Error(errData.message || 'Falha ao cadastrar. E-mail já existente ou senha inválida.');
    }

    const data = await response.json();
    jwtToken = data.accessToken;
    userProvider = data.provider || 'LOCAL';
    userName = data.fullName || fullName;
    userEmail = data.email || email;

    localStorage.setItem('fred_jwt', jwtToken);
    localStorage.setItem('fred_user_provider', userProvider);
    localStorage.setItem('fred_user_name', userName);
    localStorage.setItem('fred_user_email', userEmail);

    showAppView();
  } catch (err) {
    alert(err.message);
  }
}

function handleSocialLogin(provider) {
  // Redireciona imediatamente para o fluxo oficial OAuth2 do provedor
  window.location.href = `/oauth2/authorization/${provider.toLowerCase()}`;
}

function openTokenValidationModal(preselectedProvider) {
  const modal = document.getElementById('token-validation-modal');
  const select = document.getElementById('manual-provider-select');
  const input = document.getElementById('manual-token-input');
  if (preselectedProvider && select) {
    select.value = preselectedProvider;
  }
  if (input) input.value = '';
  if (modal) modal.classList.remove('hidden');
}

function closeTokenValidationModal() {
  const modal = document.getElementById('token-validation-modal');
  if (modal) modal.classList.add('hidden');
}

async function submitManualTokenValidation() {
  const select = document.getElementById('manual-provider-select');
  const tokenInput = document.getElementById('manual-token-input');
  const provider = select ? select.value : 'GOOGLE';
  const token = tokenInput ? tokenInput.value.trim() : '';

  if (!token) {
    alert('Por favor, informe ou cole o token do provedor para validação na API.');
    return;
  }

  const validateBtn = document.getElementById('btn-validate-manual-token');
  const originalText = validateBtn ? validateBtn.innerText : '';
  if (validateBtn) {
    validateBtn.innerText = 'Validando na API oficial...';
    validateBtn.disabled = true;
  }

  try {
    const response = await fetch('/api/v1/auth/social', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider: provider,
        token: token
      })
    });

    if (!response.ok) {
      const errData = await response.json().catch(() => ({}));
      throw new Error(errData.message || `Falha na validação do token com a API de ${provider}`);
    }

    const data = await response.json();
    jwtToken = data.accessToken;
    userProvider = data.provider || provider;
    userName = data.fullName || 'Usuário ' + provider;
    userEmail = data.email || '';

    localStorage.setItem('fred_jwt', jwtToken);
    localStorage.setItem('fred_user_provider', userProvider);
    localStorage.setItem('fred_user_name', userName);
    localStorage.setItem('fred_user_email', userEmail);

    closeTokenValidationModal();
    showAppView();
  } catch (err) {
    alert('❌ ' + err.message);
  } finally {
    if (validateBtn) {
      validateBtn.innerText = originalText;
      validateBtn.disabled = false;
    }
  }
}

function logout() {
  jwtToken = null;
  userProvider = 'LOCAL';
  userName = '';
  userEmail = '';
  localStorage.removeItem('fred_jwt');
  localStorage.removeItem('fred_user_provider');
  localStorage.removeItem('fred_user_name');
  localStorage.removeItem('fred_user_email');
  const emailInput = document.getElementById('login-email');
  const passInput = document.getElementById('login-password');
  if (emailInput) emailInput.value = '';
  if (passInput) passInput.value = '';
  showAuthView();
}

// Navigation Tabs
function switchTab(tab) {
  const tabs = ['dashboard', 'ai', 'openfinance'];
  tabs.forEach(t => {
    const content = document.getElementById(`tab-content-${t}`);
    const navBtn = document.getElementById(`nav-${t}`);
    if (t === tab) {
      content.classList.remove('hidden');
      navBtn.classList.add('active');
    } else {
      content.classList.add('hidden');
      navBtn.classList.remove('active');
    }
  });

  if (tab === 'openfinance') {
    loadConsents();
    loadXpStatus();
    loadOpenFinanceInstitutions();
  }
}

// Dashboard Functions
async function loadDashboardData() {
  try {
    const res = await fetch('/api/v1/portfolios/summary', {
      headers: { 'Authorization': `Bearer ${jwtToken}` }
    });

    if (res.status === 401) { logout(); return; }

    const summary = await res.json();
    
    document.getElementById('val-patrimony').innerText = formatCurrency(summary.totalPatrimony);
    document.getElementById('val-invested').innerText = formatCurrency(summary.totalInvested);

    const gainLossEl = document.getElementById('val-gainloss');
    gainLossEl.innerText = `${formatCurrency(summary.totalGainLoss)} (${summary.gainLossPercentage.toFixed(2)}%)`;
    if (summary.totalGainLoss >= 0) {
      gainLossEl.className = 'metric-value metric-positive';
    } else {
      gainLossEl.className = 'metric-value metric-negative';
    }

    if (summary.portfolios && summary.portfolios.length > 0) {
      currentPortfolioId = summary.portfolios[0].id;
      renderAssetsTable(summary.portfolios[0].assets);
    }
  } catch (err) {
    console.error('Erro ao carregar dados do dashboard:', err);
  }
}

function renderAssetsTable(assets) {
  currentAssets = assets || [];
  const tbody = document.getElementById('assets-table-body');
  if (!assets || assets.length === 0) {
    tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: var(--text-muted);">Nenhum ativo cadastrado ainda.</td></tr>';
    return;
  }

  tbody.innerHTML = assets.map(a => `
    <tr>
      <td>
        <div style="display: flex; align-items: baseline; gap: 0.4rem; flex-wrap: wrap;">
          <strong>${a.ticker}</strong>
          ${a.category === 'OPCOES' && a.underlyingPrice != null ? `
            <span style="font-size: 0.82rem; color: #34d399; font-weight: 600;" title="Preço atual da ação (${a.underlyingTicker || 'Ação Base'})">
              (${a.underlyingTicker ? a.underlyingTicker + ': ' : 'Ação: '}${formatCurrency(a.underlyingPrice)})
            </span>
          ` : ''}
        </div>
        ${a.category === 'OPCOES' && (a.strikePrice != null || a.expirationDate || a.underlyingPrice != null) ? `
          <div style="margin-top: 0.25rem; font-size: 0.75rem; display: flex; gap: 0.35rem; flex-wrap: wrap;">
            ${a.underlyingPrice != null ? `<span style="background: rgba(16, 185, 129, 0.15); color: #34d399; padding: 0.1rem 0.35rem; border-radius: 0.25rem; font-weight: 500;">Ação ${a.underlyingTicker ? `(${a.underlyingTicker})` : ''}: ${formatCurrency(a.underlyingPrice)}</span>` : ''}
            ${a.strikePrice != null ? `<span style="background: rgba(96, 165, 250, 0.15); color: #93c5fd; padding: 0.1rem 0.35rem; border-radius: 0.25rem; font-weight: 500;">Strike: ${formatCurrency(a.strikePrice)}</span>` : ''}
            ${a.expirationDate ? `<span style="background: rgba(167, 139, 250, 0.15); color: #c4b5fd; padding: 0.1rem 0.35rem; border-radius: 0.25rem; font-weight: 500;">Venc: ${formatDateOnly(a.expirationDate)}</span>` : ''}
          </div>
        ` : ''}
      </td>
      <td>${a.name}</td>
      <td><span class="badge">${a.category}</span></td>
      <td>${a.quantity}</td>
      <td>${formatCurrency(a.averagePrice)}</td>
      <td>
        <div>${formatCurrency(a.currentPrice)}</div>
        ${a.category === 'OPCOES' && a.underlyingPrice != null ? `
          <div style="font-size: 0.75rem; color: #34d399; margin-top: 0.15rem;" title="Preço atual da ação base">
            ${a.underlyingTicker ? a.underlyingTicker : 'Ação'}: ${formatCurrency(a.underlyingPrice)}
          </div>
        ` : ''}
      </td>
      <td><strong>${formatCurrency(a.totalValue)}</strong></td>
      <td>
        <button onclick="openAssetHistoryModal(${a.id})" style="background: none; border: none; font-size: 1.1rem; cursor: pointer;" title="Ver Histórico de Compras">📜</button>
      </td>
    </tr>
  `).join('');
}

async function openAssetHistoryModal(assetId) {
  const asset = currentAssets.find(a => a.id === assetId);
  const ticker = asset ? asset.ticker : '';
  let subtitle = asset ? (asset.name || '') : '';
  if (asset && asset.category === 'OPCOES' && (asset.strikePrice != null || asset.expirationDate || asset.underlyingPrice != null)) {
    const details = [];
    if (asset.underlyingPrice != null) details.push(`Ação (${asset.underlyingTicker || 'Base'}): ${formatCurrency(asset.underlyingPrice)}`);
    if (asset.strikePrice != null) details.push(`Strike: ${formatCurrency(asset.strikePrice)}`);
    if (asset.expirationDate) details.push(`Vencimento: ${formatDateOnly(asset.expirationDate)}`);
    subtitle += ` (${details.join(' • ')})`;
  }
  const modal = document.getElementById('asset-history-modal');
  const titleEl = document.getElementById('asset-history-title');
  const subtitleEl = document.getElementById('asset-history-subtitle');
  const tbody = document.getElementById('asset-history-table-body');
  const tfoot = document.getElementById('asset-history-table-foot');

  if (titleEl) titleEl.innerText = `📜 Histórico de Compras: ${ticker}`;
  if (subtitleEl) subtitleEl.innerText = subtitle;
  if (tbody) {
    tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 1.5rem;">Carregando histórico de compras...</td></tr>';
  }
  if (tfoot) tfoot.innerHTML = '';
  if (modal) modal.classList.remove('hidden');

  try {
    const res = await fetch(`/api/v1/portfolios/${currentPortfolioId}/assets/${assetId}/history`, {
      headers: { 'Authorization': `Bearer ${jwtToken}` }
    });

    if (!res.ok) {
      throw new Error('Falha ao buscar histórico');
    }

    const history = await res.json();
    if (!history || history.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 1.5rem;">Nenhuma transação de compra registrada para este ativo.</td></tr>';
      return;
    }

    let totalQtd = 0;
    let totalInvestido = 0;

    tbody.innerHTML = history.map(h => {
      const d = h.transactionDate ? new Date(h.transactionDate) : null;
      const dateStr = d && !isNaN(d) ? d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '-';
      totalQtd += Number(h.quantity || 0);
      totalInvestido += Number(h.totalValue || 0);

      return `
        <tr>
          <td style="padding: 0.5rem;">${dateStr}</td>
          <td style="padding: 0.5rem; text-align: right;"><strong>${h.quantity}</strong></td>
          <td style="padding: 0.5rem; text-align: right;">${formatCurrency(h.price)}</td>
          <td style="padding: 0.5rem; text-align: right;"><strong>${formatCurrency(h.totalValue)}</strong></td>
          <td style="padding: 0.5rem; text-align: center; white-space: nowrap;">
            <button onclick="handleEditTransactionQty(${assetId}, ${h.id}, ${h.quantity})" style="background: none; border: none; font-size: 1.05rem; cursor: pointer; margin-right: 6px;" title="Alterar Quantidade">✏️</button>
            <button onclick="handleDeleteTransaction(${assetId}, ${h.id})" style="background: none; border: none; font-size: 1.05rem; color: var(--accent-red); cursor: pointer;" title="Excluir Compra">🗑️</button>
          </td>
        </tr>
      `;
    }).join('');

    if (tfoot) {
      tfoot.innerHTML = `
        <tr>
          <td style="padding: 0.5rem;">Total Acumulado</td>
          <td style="padding: 0.5rem; text-align: right;">${totalQtd}</td>
          <td style="padding: 0.5rem; text-align: right; color: var(--text-muted);">-</td>
          <td style="padding: 0.5rem; text-align: right; color: var(--accent-green);">${formatCurrency(totalInvestido)}</td>
          <td style="padding: 0.5rem;"></td>
        </tr>
      `;
    }
  } catch (err) {
    console.error('Erro ao carregar histórico:', err);
    if (tbody) {
      tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--accent-red); padding: 1.5rem;">Erro ao carregar histórico de compras.</td></tr>';
    }
  }
}

async function handleEditTransactionQty(assetId, transactionId, currentQty) {
  const newQtyStr = prompt(`Informe a nova quantidade para esta compra (atual: ${currentQty}):`, currentQty);
  if (newQtyStr === null) return;
  const newQty = parseFloat(newQtyStr);
  if (isNaN(newQty) || newQty <= 0) {
    alert('Quantidade inválida. Para remover o registro, utilize o botão de excluir (🗑️).');
    return;
  }

  try {
    const res = await fetch(`/api/v1/portfolios/${currentPortfolioId}/assets/${assetId}/history/${transactionId}?quantity=${newQty}`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${jwtToken}` }
    });

    if (res.ok) {
      await loadDashboardData();
      await openAssetHistoryModal(assetId);
    } else {
      const err = await res.json().catch(() => ({}));
      alert(err.message || 'Erro ao alterar quantidade.');
    }
  } catch (err) {
    alert('Erro ao alterar quantidade: ' + err.message);
  }
}

async function handleDeleteTransaction(assetId, transactionId) {
  if (!confirm('Deseja excluir esta linha do histórico de compras?')) return;

  try {
    const res = await fetch(`/api/v1/portfolios/${currentPortfolioId}/assets/${assetId}/history/${transactionId}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${jwtToken}` }
    });

    if (res.ok) {
      const data = await res.json();
      await loadDashboardData();
      if (data.assetDeleted) {
        closeAssetHistoryModal();
        alert('Todas as compras foram excluídas. O ativo foi removido da carteira.');
      } else {
        await openAssetHistoryModal(assetId);
      }
    } else {
      const err = await res.json().catch(() => ({}));
      alert(err.message || 'Erro ao excluir compra.');
    }
  } catch (err) {
    alert('Erro ao excluir compra: ' + err.message);
  }
}

function closeAssetHistoryModal() {
  const modal = document.getElementById('asset-history-modal');
  if (modal) modal.classList.add('hidden');
}

let tickerQuoteDebounceTimer = null;

function onCategoryChange() {
  const catSelect = document.getElementById('asset-category');
  const optFields = document.getElementById('option-fields');
  if (catSelect && optFields) {
    optFields.style.display = catSelect.value === 'OPCOES' ? 'flex' : 'none';
  }
  checkTickerQuote();
}

function onTickerInput() {
  const tickerInput = document.getElementById('asset-ticker');
  if (!tickerInput) return;
  const ticker = tickerInput.value.trim().toUpperCase();
  const catSelect = document.getElementById('asset-category');
  const optFields = document.getElementById('option-fields');

  if (catSelect && ticker) {
    // Auto-detecta tipo de ativo dinamicamente pela B3 / Ticker:
    if (/^[A-Z]{4}[A-Z][0-9A-Z]+$/.test(ticker)) {
      catSelect.value = 'OPCOES';
      if (optFields) optFields.style.display = 'flex';
    } else if (/^[A-Z]{4}11[B]?$/.test(ticker)) {
      catSelect.value = 'FIIS';
      if (optFields) optFields.style.display = 'none';
    } else if (/^[A-Z]{4}[3-6]$/.test(ticker)) {
      catSelect.value = 'ACOES';
      if (optFields) optFields.style.display = 'none';
    }
  }

  clearTimeout(tickerQuoteDebounceTimer);
  if (ticker.length >= 4) {
    tickerQuoteDebounceTimer = setTimeout(() => {
      checkTickerQuote();
    }, 500);
  }
}

function clearAddAssetForm() {
  const form = document.getElementById('add-asset-form');
  if (form) form.reset();
  const tickerInput = document.getElementById('asset-ticker');
  const nameInput = document.getElementById('asset-name');
  const catInput = document.getElementById('asset-category');
  const dateInput = document.getElementById('asset-date');
  const qtyInput = document.getElementById('asset-qty');
  const priceInput = document.getElementById('asset-price');
  const strikeInput = document.getElementById('asset-strike');
  const expInput = document.getElementById('asset-expiration');
  const optFields = document.getElementById('option-fields');
  const optUnderlyingInfo = document.getElementById('option-underlying-info');
  const optUnderlyingText = document.getElementById('option-underlying-text');

  if (tickerInput) tickerInput.value = '';
  if (nameInput) nameInput.value = '';
  if (catInput) catInput.value = 'ACOES';
  if (dateInput) dateInput.value = new Date().toISOString().split('T')[0];
  if (qtyInput) qtyInput.value = '';
  if (priceInput) {
    priceInput.value = '';
    priceInput.placeholder = 'Preço Médio (R$)';
  }
  if (strikeInput) strikeInput.value = '';
  if (expInput) expInput.value = '';
  if (optFields) optFields.style.display = 'none';
  if (optUnderlyingInfo) optUnderlyingInfo.style.display = 'none';
  if (optUnderlyingText) optUnderlyingText.innerText = 'Ação Base: -';

  const badge = document.getElementById('ticker-quote-badge');
  if (badge) {
    badge.style.display = 'none';
    badge.innerText = '';
  }
}

function openAddAssetModal() {
  const box = document.getElementById('add-asset-box');
  const isHidden = box.classList.contains('hidden');
  if (isHidden) {
    clearAddAssetForm();
    box.classList.remove('hidden');
    const dateInput = document.getElementById('asset-date');
    if (dateInput && !dateInput.value) {
      dateInput.value = new Date().toISOString().split('T')[0];
    }
    const tickerInput = document.getElementById('asset-ticker');
    if (tickerInput) tickerInput.focus();
  } else {
    box.classList.add('hidden');
  }
}

function closeAddAssetModal() {
  clearAddAssetForm();
  document.getElementById('add-asset-box').classList.add('hidden');
}

let lastQuoteUnderlyingTicker = null;

async function checkTickerQuote() {
  const tickerInput = document.getElementById('asset-ticker');
  if (!tickerInput) return;
  const ticker = tickerInput.value.trim().toUpperCase();
  const catSelect = document.getElementById('asset-category');
  const optFields = document.getElementById('option-fields');
  const optUnderlyingInfo = document.getElementById('option-underlying-info');
  const optUnderlyingText = document.getElementById('option-underlying-text');

  if (!ticker || ticker.length < 3) {
    const badge = document.getElementById('ticker-quote-badge');
    if (badge) badge.style.display = 'none';
    if (optUnderlyingInfo) optUnderlyingInfo.style.display = 'none';
    return;
  }

  // Auto-detecta Opções, FIIs ou Ações brasileiras
  if (/^[A-Z]{4}[A-Z][0-9A-Z]+$/.test(ticker) && catSelect) {
    catSelect.value = 'OPCOES';
    if (optFields) optFields.style.display = 'flex';
  } else if (/^[A-Z]{4}11[B]?$/.test(ticker) && catSelect) {
    catSelect.value = 'FIIS';
    if (optFields) optFields.style.display = 'none';
  } else if (/^[A-Z]{4}[3-6]$/.test(ticker) && catSelect) {
    catSelect.value = 'ACOES';
    if (optFields) optFields.style.display = 'none';
  }

  const category = catSelect?.value || 'ACOES';
  const badge = document.getElementById('ticker-quote-badge');
  if (!badge) return;

  badge.style.display = 'inline-block';
  badge.style.background = 'var(--bg-card)';
  badge.style.color = 'var(--text-muted)';
  badge.innerText = `Buscando cotação ao vivo...`;

  try {
    const res = await fetch(`/api/v1/portfolios/quote/${encodeURIComponent(ticker)}?category=${category}`, {
      headers: { 'Authorization': `Bearer ${jwtToken}` }
    });
    if (res.ok) {
      const data = await res.json();
      if (data.found && data.price != null) {
        badge.style.background = '#065f46';
        badge.style.color = '#34d399';
        const sourceName = data.source || 'Ao Vivo';
        let badgeText = `${sourceName}: ${formatCurrency(data.price)}`;
        if (data.underlyingPrice != null) {
          badgeText += ` | Ação ${data.underlyingTicker ? '(' + data.underlyingTicker + ')' : ''}: ${formatCurrency(data.underlyingPrice)}`;
        }
        badge.innerText = badgeText;

        lastQuoteUnderlyingTicker = data.underlyingTicker || null;

        // Atualiza categoria automaticamente caso a B3/servidor tenha retornado uma categoria detectada
        if (data.category && catSelect) {
          catSelect.value = data.category;
          if (optFields) {
            optFields.style.display = data.category === 'OPCOES' ? 'flex' : 'none';
          }
        }

        // Auto-preenche Strike e Vencimento caso encontrados
        const strikeInput = document.getElementById('asset-strike');
        if (strikeInput && data.strikePrice != null && !strikeInput.value) {
          strikeInput.value = data.strikePrice;
        }
        const expInput = document.getElementById('asset-expiration');
        if (expInput && data.expirationDate && !expInput.value) {
          expInput.value = data.expirationDate;
        }
        if ((data.strikePrice != null || data.expirationDate || (catSelect && catSelect.value === 'OPCOES')) && optFields) {
          optFields.style.display = 'flex';
        }

        // Exibe informação da ação base no box de opções
        if (optUnderlyingInfo && optUnderlyingText) {
          if (data.underlyingPrice != null) {
            optUnderlyingInfo.style.display = 'flex';
            optUnderlyingText.innerText = `📈 Ação Base: ${data.underlyingTicker || ''} — Cotação Atual: ${formatCurrency(data.underlyingPrice)}`;
          } else {
            optUnderlyingInfo.style.display = 'none';
          }
        }

        // Preenche o nome do ativo se o backend retornar e o input estiver vazio ou igual ao ticker
        const nameInput = document.getElementById('asset-name');
        if (nameInput && data.name && (!nameInput.value || nameInput.value.trim() === ticker)) {
          nameInput.value = data.name.trim();
        }

        const priceInput = document.getElementById('asset-price');
        if (priceInput && !priceInput.value) {
          priceInput.placeholder = `Sugestão: ${data.price.toFixed(2)}`;
        }
      } else {
        badge.style.background = '#374151';
        badge.style.color = '#9ca3af';
        badge.innerText = `Cotação ao vivo não encontrada`;
        if (optUnderlyingInfo) optUnderlyingInfo.style.display = 'none';
      }
    } else {
      badge.style.display = 'none';
      if (optUnderlyingInfo) optUnderlyingInfo.style.display = 'none';
    }
  } catch (err) {
    console.debug('Erro ao consultar cotação:', err);
    badge.style.display = 'none';
    if (optUnderlyingInfo) optUnderlyingInfo.style.display = 'none';
  }
}

async function refreshLiveQuotes() {
  const btn = event?.target;
  const originalText = btn ? btn.innerText : '';
  if (btn) {
    btn.innerText = '🔄 Atualizando...';
    btn.disabled = true;
  }
  try {
    await loadDashboardData();
  } finally {
    if (btn) {
      btn.innerText = originalText;
      btn.disabled = false;
    }
  }
}

async function handleAddAsset(e) {
  e.preventDefault();
  if (!currentPortfolioId) return;

  const ticker = document.getElementById('asset-ticker').value.trim().toUpperCase();
  const name = document.getElementById('asset-name').value.trim();
  const category = document.getElementById('asset-category').value;
  const purchaseDate = document.getElementById('asset-date')?.value || null;
  const quantity = parseFloat(document.getElementById('asset-qty').value);
  const averagePrice = parseFloat(document.getElementById('asset-price').value);

  const strikeVal = document.getElementById('asset-strike')?.value;
  const strikePrice = strikeVal ? parseFloat(strikeVal) : null;
  const expirationDate = document.getElementById('asset-expiration')?.value || null;

  try {
    const res = await fetch(`/api/v1/portfolios/${currentPortfolioId}/assets`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${jwtToken}`
      },
      body: JSON.stringify({
        ticker,
        name,
        category,
        purchaseDate,
        quantity,
        averagePrice,
        strikePrice,
        expirationDate,
        underlyingTicker: lastQuoteUnderlyingTicker || null
      })
    });

    if (res.ok) {
      closeAddAssetModal();
      loadDashboardData();
    } else {
      const errData = await res.json().catch(() => ({}));
      alert(errData.message || 'Erro ao adicionar ativo.');
    }
  } catch (err) {
    alert('Erro ao adicionar ativo: ' + err.message);
  }
}

async function deleteAsset(assetId) {
  if (!confirm('Deseja remover este ativo da carteira?')) return;

  try {
    const res = await fetch(`/api/v1/portfolios/${currentPortfolioId}/assets/${assetId}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${jwtToken}` }
    });

    if (res.ok) loadDashboardData();
  } catch (err) {
    console.error('Erro ao deletar ativo:', err);
  }
}

// AI Functions
function saveAiApiKey() {
  const input = document.getElementById('ai-api-key-input');
  if (input) {
    localStorage.setItem('fred_ai_api_key', input.value.trim());
  }
}

function setupAiProvider() {
  const select = document.getElementById('ai-provider-select');
  const msgEl = document.getElementById('ai-auto-select-msg');
  const apiKeyInput = document.getElementById('ai-api-key-input');

  const savedKey = localStorage.getItem('fred_ai_api_key');
  if (savedKey && apiKeyInput) {
    apiKeyInput.value = savedKey;
  }

  const savedProfile = localStorage.getItem('fred_ai_risk_profile') || 'MODERADO';
  const headerRisk = document.getElementById('header-risk-profile-select');
  const tabRisk = document.getElementById('ai-risk-profile-select');
  if (headerRisk) headerRisk.value = savedProfile;
  if (tabRisk) tabRisk.value = savedProfile;

  if (!select) return;

  if (userProvider === 'GOOGLE') {
    select.value = 'GEMINI';
    if (msgEl) {
      msgEl.innerHTML = '💡 Como você fez login via <strong>Google</strong>, a IA <strong>Google Gemini</strong> foi selecionada automaticamente!';
    }
  } else {
    if (msgEl) {
      msgEl.innerHTML = `💡 Usuário logado via <strong>${userProvider}</strong>. Selecione a IA de mercado desejada.`;
    }
  }
  onAiProviderChange();
}

function onRiskProfileChange(val) {
  if (!val) return;
  localStorage.setItem('fred_ai_risk_profile', val);
  const headerRisk = document.getElementById('header-risk-profile-select');
  const tabRisk = document.getElementById('ai-risk-profile-select');
  if (headerRisk && headerRisk.value !== val) headerRisk.value = val;
  if (tabRisk && tabRisk.value !== val) tabRisk.value = val;
}

function onAiProviderChange() {
  const select = document.getElementById('ai-provider-select');
  const badge = document.getElementById('active-ai-badge');
  if (!select || !badge) return;

  const providerNames = {
    'GEMINI': 'Google Gemini 2.5 Flash',
    'OPENAI': 'OpenAI GPT-4o-mini',
    'CLAUDE': 'Anthropic Claude 3.5 Sonnet',
    'DEEPSEEK': 'DeepSeek V3'
  };

  const badgeColors = {
    'GEMINI': '#4285F4',
    'OPENAI': '#10a37f',
    'CLAUDE': '#d97706',
    'DEEPSEEK': '#8b5cf6'
  };

  const val = select.value;
  badge.innerText = providerNames[val] || val;
  badge.style.background = badgeColors[val] || '#2563eb';

  const keyInput = document.getElementById('ai-api-key-input');
  if (keyInput) {
    if (val === 'OPENAI') {
      keyInput.placeholder = '🔑 OpenAI API Key';
      keyInput.title = 'Cole sua chave da API da OpenAI para respostas ao vivo';
    } else if (val === 'CLAUDE') {
      keyInput.placeholder = '🔑 Claude API Key';
      keyInput.title = 'Cole sua chave da API da Anthropic Claude';
    } else if (val === 'DEEPSEEK') {
      keyInput.placeholder = '🔑 DeepSeek API Key';
      keyInput.title = 'Cole sua chave da API da DeepSeek para respostas ao vivo';
    } else {
      keyInput.placeholder = '🔑 Gemini API Key';
      keyInput.title = 'Cole sua chave da API do Google Gemini (Google AI Studio) para respostas ao vivo pela internet';
    }
  }
}

function formatAiText(text) {
  if (!text) return '';
  let escaped = escapeHtml(text);
  escaped = escaped.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
  return escaped;
}

async function runAiAnalysis() {
  const resultDiv = document.getElementById('ai-analysis-result');
  const selectedProvider = document.getElementById('ai-provider-select')?.value || 'GEMINI';
  const riskProfile = document.getElementById('header-risk-profile-select')?.value || document.getElementById('ai-risk-profile-select')?.value || localStorage.getItem('fred_ai_risk_profile') || 'MODERADO';
  const apiKey = localStorage.getItem('fred_ai_api_key') || document.getElementById('ai-api-key-input')?.value || '';

  resultDiv.innerHTML = `<p style="color: var(--accent-blue);">Analisando carteira com inteligência artificial (${selectedProvider}) para perfil ${riskProfile}...</p>`;

  try {
    const res = await fetch('/api/v1/ai/analyze', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${jwtToken}`
      },
      body: JSON.stringify({ riskProfile: riskProfile, provider: selectedProvider, apiKey: apiKey })
    });

    const data = await res.json();
    resultDiv.innerHTML = `
      <div style="margin-bottom: 0.75rem; display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap;">
        <span class="badge" style="background: var(--bg-primary); border: 1px solid var(--border-color); color: var(--text-muted); font-size: 0.8rem;">Modelo: ${data.model || data.provider}</span>
        <span class="badge" style="background: rgba(59, 130, 246, 0.15); border: 1px solid var(--accent-blue); color: var(--accent-blue); font-size: 0.8rem;">Perfil: ${riskProfile}</span>
      </div>
      <div style="margin-bottom: 1.25rem;">
        <h4 style="color: var(--accent-blue); font-size: 1.1rem; margin-bottom: 0.5rem;">📋 Resumo Executivo & Recomendações</h4>
        <div style="white-space: pre-wrap; line-height: 1.6; background: var(--bg-primary); padding: 1rem; border-radius: 0.5rem; border: 1px solid var(--border-color);">${formatAiText(data.summary)}</div>
      </div>
      <div style="margin-bottom: 1rem;">
        <h4 style="color: var(--accent-purple); font-size: 1.1rem; margin-bottom: 0.5rem;">⚖️ Avaliação de Risco</h4>
        <p style="white-space: pre-wrap; line-height: 1.5;">${formatAiText(data.riskAssessment)}</p>
      </div>
      ${data.diversificationAdvice && data.diversificationAdvice.length > 0 ? `
      <div style="margin-bottom: 1rem;">
        <h4 style="color: var(--accent-green); font-size: 1.1rem; margin-bottom: 0.5rem;">💡 Conselhos de Diversificação</h4>
        <ul style="padding-left: 1.25rem; line-height: 1.6;">${data.diversificationAdvice.map(a => `<li>${formatAiText(a)}</li>`).join('')}</ul>
      </div>` : ''}
      ${data.recommendedActions && data.recommendedActions.length > 0 ? `
      <div>
        <h4 style="color: #f59e0b; font-size: 1.1rem; margin-bottom: 0.5rem;">🎯 Ações Recomendadas (Manutenção / Alteração)</h4>
        <ul style="padding-left: 1.25rem; line-height: 1.6;">${data.recommendedActions.map(a => `<li>${formatAiText(a)}</li>`).join('')}</ul>
      </div>` : ''}
    `;
  } catch (err) {
    resultDiv.innerHTML = '<p style="color: var(--accent-red);">Erro ao comunicar com a API de IA.</p>';
  }
}

function escapeHtml(text) {
  if (!text) return '';
  const div = document.createElement('div');
  div.innerText = text;
  return div.innerHTML;
}

async function handleChatSubmit(e) {
  e.preventDefault();
  const input = document.getElementById('chat-input');
  const prompt = input.value.trim();
  if (!prompt) return;

  const selectedProvider = document.getElementById('ai-provider-select')?.value || 'GEMINI';
  const riskProfile = document.getElementById('header-risk-profile-select')?.value || document.getElementById('ai-risk-profile-select')?.value || localStorage.getItem('fred_ai_risk_profile') || 'MODERADO';
  const apiKey = localStorage.getItem('fred_ai_api_key') || document.getElementById('ai-api-key-input')?.value || '';

  const chatContainer = document.getElementById('chat-messages');
  chatContainer.innerHTML += `<div style="margin-bottom: 0.5rem; text-align: right;"><strong>Você:</strong> ${escapeHtml(prompt)}</div>`;
  input.value = '';

  try {
    const res = await fetch('/api/v1/ai/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${jwtToken}`
      },
      body: JSON.stringify({ prompt, provider: selectedProvider, riskProfile: riskProfile, apiKey: apiKey })
    });

    const data = await res.json();
    chatContainer.innerHTML += `<div style="margin-bottom: 0.75rem; color: var(--accent-blue); white-space: pre-wrap; line-height: 1.5; background: var(--bg-primary); padding: 0.75rem; border-radius: 0.5rem; border: 1px solid var(--border-color);"><strong>Assistente [${data.model || data.provider}] (${data.timestamp}):</strong>\n${formatAiText(data.reply)}</div>`;
    chatContainer.scrollTop = chatContainer.scrollHeight;
  } catch (err) {
    chatContainer.innerHTML += `<div style="margin-bottom: 0.5rem; color: var(--accent-red);">Erro ao responder.</div>`;
  }
}

// Open Finance Functions
let currentXpConsentId = null;

async function loadOpenFinanceInstitutions() {
  try {
    const res = await fetch('/api/v1/open-finance/institutions');
    const insts = await res.json();

    const grid = document.getElementById('institutions-grid');
    // Filtra a XP da grade secundária para não duplicar com o card em destaque
    const otherInsts = insts.filter(i => i.id !== 'xp');
    grid.innerHTML = otherInsts.map(i => `
      <div style="background: var(--bg-primary); padding: 1rem; border-radius: 0.5rem; border: 1px solid var(--border-color); display: flex; flex-direction: column; align-items: center; gap: 0.5rem;">
        <span style="font-size: 2rem;">${i.logoUrl}</span>
        <strong>${i.name}</strong>
        <button class="btn-primary" style="padding: 0.4rem 0.8rem; font-size: 0.875rem;" onclick="connectInstitution('${i.id}')">Conectar</button>
      </div>
    `).join('');
  } catch (err) {
    console.error('Erro ao carregar instituições:', err);
  }
}

async function loadXpStatus() {
  try {
    const res = await fetch('/api/v1/open-finance/xp/status', {
      headers: { 'Authorization': `Bearer ${jwtToken}` }
    });
    if (!res.ok) return;
    const data = await res.json();

    const badge = document.getElementById('xp-status-badge');
    const info = document.getElementById('xp-account-info');
    const btnConnect = document.getElementById('btn-xp-connect');
    const btnSync = document.getElementById('btn-xp-sync');
    const btnDisconnect = document.getElementById('btn-xp-disconnect');

    if (data.connected) {
      currentXpConsentId = data.consentId;
      if (badge) {
        badge.innerText = '🟢 Conectado ao Open Finance XP';
        badge.style.background = 'rgba(16, 185, 129, 0.2)';
        badge.style.border = '1px solid var(--accent-green)';
        badge.style.color = 'var(--accent-green)';
      }
      if (info) {
        info.style.display = 'block';
        info.innerHTML = `✅ Conta XP: <strong>${escapeHtml(data.accountNumber)}</strong> • Consentimento ativo até ${data.expiresAt ? formatDateOnly(data.expiresAt) : '12 meses'}`;
      }
      if (btnConnect) btnConnect.style.display = 'none';
      if (btnSync) btnSync.style.display = 'inline-flex';
      if (btnDisconnect) btnDisconnect.style.display = 'inline-flex';
    } else {
      currentXpConsentId = null;
      if (badge) {
        badge.innerText = '⚪ Não Conectado';
        badge.style.background = 'var(--bg-primary)';
        badge.style.border = '1px solid var(--border-color)';
        badge.style.color = 'var(--text-muted)';
      }
      if (info) info.style.display = 'none';
      if (btnConnect) btnConnect.style.display = 'inline-flex';
      if (btnSync) btnSync.style.display = 'none';
      if (btnDisconnect) btnDisconnect.style.display = 'none';
    }
  } catch (err) {
    console.error('Erro ao verificar status XP:', err);
  }
}

async function openXpConnectModal() {
  const modal = document.getElementById('xp-connect-modal');
  if (modal) modal.classList.remove('hidden');

  try {
    const res = await fetch('/api/v1/open-finance/xp/config', {
      headers: { 'Authorization': `Bearer ${jwtToken}` }
    });
    if (res.ok) {
      const config = await res.json();
      const clientIdInput = document.getElementById('xp-client-id');
      if (clientIdInput && config.clientId) {
        clientIdInput.value = config.clientId;
      }
    }
  } catch (err) {
    console.error('Erro ao carregar configurações XP:', err);
  }
}

function closeXpConnectModal() {
  const modal = document.getElementById('xp-connect-modal');
  if (modal) modal.classList.add('hidden');
}

async function startOfficialXpAuth() {
  if (!jwtToken) {
    alert('Por favor, faça login antes de autorizar o Open Finance.');
    return;
  }

  const clientId = document.getElementById('xp-client-id')?.value.trim() || '';
  const clientSecret = document.getElementById('xp-client-secret')?.value.trim() || '';

  // Se o usuário preencheu credenciais no modal, salva primeiro no servidor
  if (clientId || clientSecret) {
    try {
      await fetch('/api/v1/open-finance/xp/config', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${jwtToken}`
        },
        body: JSON.stringify({
          clientId: clientId,
          clientSecret: clientSecret
        })
      });
    } catch (err) {
      console.warn('Não foi possível salvar configurações previamente:', err);
    }
  }

  try {
    const res = await fetch('/api/v1/open-finance/xp/consent', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${jwtToken}`
      }
    });

    const data = await res.json().catch(() => ({}));

    if (!res.ok) {
      alert(data.message || 'Falha ao iniciar consentimento oficial com a XP. Verifique suas credenciais.');
      return;
    }

    if (data.authUrl) {
      // Redireciona o usuário para o portal oficial de autorização da XP / Pluggy
      window.location.href = data.authUrl;
    } else {
      alert('Não foi possível gerar a URL de autorização da XP.');
    }
  } catch (err) {
    alert('Erro no Open Finance XP: ' + err.message);
  }
}

async function submitXpConnect(e) {
  e.preventDefault();
  const clientId = document.getElementById('xp-client-id')?.value.trim() || '';
  const clientSecret = document.getElementById('xp-client-secret')?.value.trim() || '';
  const apiToken = document.getElementById('xp-api-token')?.value.trim() || '';
  const replacePortfolio = document.getElementById('xp-replace-portfolio')?.checked ?? true;

  if (clientId || clientSecret) {
    try {
      await fetch('/api/v1/open-finance/xp/config', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${jwtToken}`
        },
        body: JSON.stringify({
          clientId: clientId,
          clientSecret: clientSecret
        })
      });
    } catch (ignored) {}
  }

  const btn = document.getElementById('btn-xp-submit');
  const originalText = btn ? btn.innerText : 'Sincronizar Custódia Oficial';
  if (btn) {
    btn.disabled = true;
    btn.innerText = 'Consultando API oficial da XP...';
  }

  try {
    const res = await fetch('/api/v1/open-finance/xp/connect', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${jwtToken}`
      },
      body: JSON.stringify({
        clientId: clientId,
        clientSecret: clientSecret,
        apiToken: apiToken,
        replacePortfolio: replacePortfolio
      })
    });

    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(data.message || 'Falha na conexão oficial com a XP.');
    }

    closeXpConnectModal();
    alert(data.message || 'Custódia oficial sincronizada com sucesso!');
    await loadXpStatus();
    await loadConsents();
    await loadDashboardData();
  } catch (err) {
    alert('Erro no Open Finance Oficial XP: ' + err.message);
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.innerText = originalText;
    }
  }
}

async function syncXpNow() {
  try {
    const res = await fetch('/api/v1/open-finance/xp/sync', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${jwtToken}` }
    });
    const data = await res.json();
    alert(data.message || 'Sincronização com a XP concluída!');
    await loadXpStatus();
    await loadDashboardData();
    await loadConsents();
  } catch (err) {
    alert('Erro ao sincronizar com a XP: ' + err.message);
  }
}

async function disconnectXp() {
  if (!confirm('Deseja realmente revogar a conexão Open Finance com a XP Investimentos?')) {
    return;
  }
  if (!currentXpConsentId) return;

  try {
    const res = await fetch(`/api/v1/open-finance/consents/${currentXpConsentId}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${jwtToken}` }
    });
    if (res.ok) {
      alert('Conexão com a XP revogada.');
      await loadXpStatus();
      await loadConsents();
    } else {
      alert('Erro ao desconectar da XP.');
    }
  } catch (err) {
    alert('Erro ao revogar: ' + err.message);
  }
}

async function connectInstitution(instId) {
  if (instId === 'xp') {
    openXpConnectModal();
    return;
  }

  try {
    const res = await fetch('/api/v1/open-finance/consent', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${jwtToken}`
      },
      body: JSON.stringify({ institutionId: instId })
    });

    const data = await res.json();
    if (confirm(`Autorizar solicitação de consentimento com a instituição ${data.institutionName}?`)) {
      const authRes = await fetch(`/api/v1/open-finance/consent/${data.consentId}/authorize`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${jwtToken}` }
      });

      const syncData = await authRes.json();
      alert(syncData.message);
      loadDashboardData();
      loadConsents();
    }
  } catch (err) {
    alert('Erro no Open Finance: ' + err.message);
  }
}

async function loadConsents() {
  try {
    const res = await fetch('/api/v1/open-finance/consents', {
      headers: { 'Authorization': `Bearer ${jwtToken}` }
    });

    const consents = await res.json();
    const container = document.getElementById('consents-list');

    if (!consents || consents.length === 0) {
      container.innerHTML = '<p style="color: var(--text-muted);">Nenhuma instituição conectada no momento.</p>';
      return;
    }

    container.innerHTML = consents.map(c => `
      <div style="background: var(--bg-primary); padding: 0.75rem 1rem; border-radius: 0.375rem; border: 1px solid var(--border-color); margin-bottom: 0.5rem; display: flex; justify-content: space-between; align-items: center;">
        <div>
          <strong>${escapeHtml(c.institutionName)}</strong>
          <span style="font-size: 0.8rem; color: var(--text-muted); display: block;">ID: ${escapeHtml(c.consentId)}</span>
        </div>
        <div style="display: flex; align-items: center; gap: 0.5rem;">
          <span class="badge" style="background: ${c.status === 'AUTHORIZED' ? 'var(--accent-green)' : 'var(--text-muted)'}; color: black;">${c.status}</span>
          ${c.status === 'AUTHORIZED' ? `<button class="btn-secondary" style="padding: 0.25rem 0.5rem; font-size: 0.75rem; color: var(--accent-red);" onclick="revokeConsentById(${c.id})">Revogar</button>` : ''}
        </div>
      </div>
    `).join('');
  } catch (err) {
    console.error('Erro ao carregar consentimentos:', err);
  }
}

async function revokeConsentById(id) {
  if (!confirm('Deseja realmente revogar este consentimento?')) return;
  try {
    const res = await fetch(`/api/v1/open-finance/consents/${id}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${jwtToken}` }
    });
    if (res.ok) {
      alert('Consentimento revogado com sucesso.');
      await loadConsents();
      await loadXpStatus();
    }
  } catch (err) {
    alert('Erro ao revogar: ' + err.message);
  }
}

// Helper Functions
function formatCurrency(val) {
  if (val === undefined || val === null) return 'R$ 0,00';
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(val);
}

function formatDateOnly(dateStr) {
  if (!dateStr) return '-';
  const parts = dateStr.split('-');
  if (parts.length === 3) {
    return `${parts[2]}/${parts[1]}/${parts[0]}`;
  }
  const d = new Date(dateStr);
  return !isNaN(d) ? d.toLocaleDateString('pt-BR') : dateStr;
}


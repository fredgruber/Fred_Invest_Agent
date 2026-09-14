// State Management
let jwtToken = localStorage.getItem('fred_jwt') || null;
let userProvider = localStorage.getItem('fred_user_provider') || 'LOCAL';
let userName = localStorage.getItem('fred_user_name') || 'Usuário';
let userEmail = localStorage.getItem('fred_user_email') || '';
let currentPortfolioId = null;
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
      localStorage.setItem('fred_user_email', userEmail);
      localStorage.setItem('fred_user_name', userName);
    } catch (e) {
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

  if (tab === 'openfinance') loadConsents();
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
  const tbody = document.getElementById('assets-table-body');
  if (!assets || assets.length === 0) {
    tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: var(--text-muted);">Nenhum ativo cadastrado ainda.</td></tr>';
    return;
  }

  tbody.innerHTML = assets.map(a => `
    <tr>
      <td><strong>${a.ticker}</strong></td>
      <td>${a.name}</td>
      <td><span class="badge">${a.category}</span></td>
      <td>${a.quantity}</td>
      <td>${formatCurrency(a.averagePrice)}</td>
      <td>${formatCurrency(a.currentPrice)}</td>
      <td><strong>${formatCurrency(a.totalValue)}</strong></td>
      <td>
        <button onclick="editAssetPrice(${a.id}, '${a.ticker}')" style="background: none; border: none; color: var(--accent-blue); cursor: pointer; margin-right: 8px;" title="Editar Preço Atual">✏️</button>
        <button onclick="deleteAsset(${a.id})" style="background: none; border: none; color: var(--accent-red); cursor: pointer;" title="Deletar Ativo">🗑️</button>
      </td>
    </tr>
  `).join('');
}

async function editAssetPrice(assetId, ticker) {
  const newPriceStr = prompt(`Digite o novo preço atual para ${ticker} (Use ponto para decimais):`);
  if (!newPriceStr) return;
  const newPrice = parseFloat(newPriceStr.replace(',', '.'));
  if (isNaN(newPrice) || newPrice < 0) {
    alert('Preço inválido.');
    return;
  }

  try {
    const res = await fetch(`/api/v1/portfolios/${currentPortfolioId}/assets/${assetId}/price?price=${newPrice}`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${jwtToken}` }
    });

    if (res.ok) {
      loadDashboardData();
    } else {
      alert('Erro ao atualizar preço.');
    }
  } catch (err) {
    console.error('Erro:', err);
  }
}

function clearAddAssetForm() {
  const form = document.getElementById('add-asset-form');
  if (form) form.reset();
  const tickerInput = document.getElementById('asset-ticker');
  const nameInput = document.getElementById('asset-name');
  const catInput = document.getElementById('asset-category');
  const qtyInput = document.getElementById('asset-qty');
  const priceInput = document.getElementById('asset-price');
  if (tickerInput) tickerInput.value = '';
  if (nameInput) nameInput.value = '';
  if (catInput) catInput.value = 'ACOES';
  if (qtyInput) qtyInput.value = '';
  if (priceInput) {
    priceInput.value = '';
    priceInput.placeholder = 'Preço Médio (R$)';
  }
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

async function checkTickerQuote() {
  const tickerInput = document.getElementById('asset-ticker');
  if (!tickerInput) return;
  const ticker = tickerInput.value.trim().toUpperCase();
  const catSelect = document.getElementById('asset-category');
  let category = catSelect?.value || 'ACOES';
  const badge = document.getElementById('ticker-quote-badge');
  if (!badge) return;

  if (!ticker || ticker.length < 3) {
    badge.style.display = 'none';
    return;
  }

  // Auto-detecta Opção brasileira (ex: VALEJ854, PETRJ300) se ainda não estiver selecionada
  if (/^[A-Z]{4}[A-Z][0-9A-Z]+$/.test(ticker) && catSelect && catSelect.value !== 'OPCOES') {
    catSelect.value = 'OPCOES';
    category = 'OPCOES';
  }

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
        badge.innerText = `${sourceName}: ${formatCurrency(data.price)}`;

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
      }
    } else {
      badge.style.display = 'none';
    }
  } catch (err) {
    console.debug('Erro ao consultar cotação:', err);
    badge.style.display = 'none';
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
  const quantity = parseFloat(document.getElementById('asset-qty').value);
  const averagePrice = parseFloat(document.getElementById('asset-price').value);

  try {
    const res = await fetch(`/api/v1/portfolios/${currentPortfolioId}/assets`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${jwtToken}`
      },
      body: JSON.stringify({ ticker, name, category, quantity, averagePrice })
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
}

async function runAiAnalysis() {
  const resultDiv = document.getElementById('ai-analysis-result');
  const selectedProvider = document.getElementById('ai-provider-select')?.value || 'GEMINI';
  const apiKey = localStorage.getItem('fred_ai_api_key') || document.getElementById('ai-api-key-input')?.value || '';

  resultDiv.innerHTML = `<p style="color: var(--accent-blue);">Analisando carteira com inteligência artificial (${selectedProvider})...</p>`;

  try {
    const res = await fetch('/api/v1/ai/analyze', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${jwtToken}`
      },
      body: JSON.stringify({ riskProfile: 'MODERADO', provider: selectedProvider, apiKey: apiKey })
    });

    const data = await res.json();
    resultDiv.innerHTML = `
      <div style="margin-bottom: 0.5rem;">
        <span class="badge" style="background: var(--bg-primary); border: 1px solid var(--border-color); color: var(--text-muted); font-size: 0.8rem;">Modelo: ${data.model || data.provider}</span>
      </div>
      <div style="margin-bottom: 1rem;">
        <h4 style="color: var(--accent-blue); font-size: 1.1rem; margin-bottom: 0.5rem;">Resumo Executivo</h4>
        <p>${data.summary}</p>
      </div>
      <div style="margin-bottom: 1rem;">
        <h4 style="color: var(--accent-purple); font-size: 1.1rem; margin-bottom: 0.5rem;">Avaliação de Risco</h4>
        <p>${data.riskAssessment}</p>
      </div>
      <div style="margin-bottom: 1rem;">
        <h4 style="color: var(--accent-green); font-size: 1.1rem; margin-bottom: 0.5rem;">Conselhos de Diversificação</h4>
        <ul>${data.diversificationAdvice.map(a => `<li>${a}</li>`).join('')}</ul>
      </div>
      <div>
        <h4 style="color: #f59e0b; font-size: 1.1rem; margin-bottom: 0.5rem;">Ações Recomendadas</h4>
        <ul>${data.recommendedActions.map(a => `<li>${a}</li>`).join('')}</ul>
      </div>
    `;
  } catch (err) {
    resultDiv.innerHTML = '<p style="color: var(--accent-red);">Erro ao comunicar com a API de IA.</p>';
  }
}

async function handleChatSubmit(e) {
  e.preventDefault();
  const input = document.getElementById('chat-input');
  const prompt = input.value.trim();
  if (!prompt) return;

  const selectedProvider = document.getElementById('ai-provider-select')?.value || 'GEMINI';
  const apiKey = localStorage.getItem('fred_ai_api_key') || document.getElementById('ai-api-key-input')?.value || '';

  const chatContainer = document.getElementById('chat-messages');
  chatContainer.innerHTML += `<div style="margin-bottom: 0.5rem; text-align: right;"><strong>Você:</strong> ${prompt}</div>`;
  input.value = '';

  try {
    const res = await fetch('/api/v1/ai/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${jwtToken}`
      },
      body: JSON.stringify({ prompt, provider: selectedProvider, apiKey: apiKey })
    });

    const data = await res.json();
    chatContainer.innerHTML += `<div style="margin-bottom: 0.5rem; color: var(--accent-blue);"><strong>Assistente [${data.model || data.provider}] (${data.timestamp}):</strong> ${data.reply}</div>`;
    chatContainer.scrollTop = chatContainer.scrollHeight;
  } catch (err) {
    chatContainer.innerHTML += `<div style="margin-bottom: 0.5rem; color: var(--accent-red);">Erro ao responder.</div>`;
  }
}

// Open Finance Functions
async function loadOpenFinanceInstitutions() {
  try {
    const res = await fetch('/api/v1/open-finance/institutions');
    const insts = await res.json();

    const grid = document.getElementById('institutions-grid');
    grid.innerHTML = insts.map(i => `
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

async function connectInstitution(instId) {
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
      // Simular autorização e sincronização de dados
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
          <strong>${c.institutionName}</strong>
          <span style="font-size: 0.8rem; color: var(--text-muted); display: block;">ID: ${c.consentId}</span>
        </div>
        <span class="badge" style="background: var(--accent-green); color: black;">${c.status}</span>
      </div>
    `).join('');
  } catch (err) {
    console.error('Erro ao carregar consentimentos:', err);
  }
}

// Helper Functions
function formatCurrency(val) {
  if (val === undefined || val === null) return 'R$ 0,00';
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(val);
}


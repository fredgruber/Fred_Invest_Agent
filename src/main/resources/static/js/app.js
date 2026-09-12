// State Management
let jwtToken = localStorage.getItem('fred_jwt') || null;
let currentPortfolioId = null;
let deferredPrompt = null;

// PWA Service Worker Registration
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js')
      .then(reg => console.log('ServiceWorker registrado:', reg.scope))
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
  loadDashboardData();
  loadOpenFinanceInstitutions();
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

    if (!response.ok) throw new Error('Falha no login. Verifique e-mail e senha.');

    const data = await response.json();
    jwtToken = data.accessToken;
    localStorage.setItem('fred_jwt', jwtToken);
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

    if (!response.ok) throw new Error('Falha ao cadastrar. E-mail já existente ou senha inválida.');

    const data = await response.json();
    jwtToken = data.accessToken;
    localStorage.setItem('fred_jwt', jwtToken);
    showAppView();
  } catch (err) {
    alert(err.message);
  }
}

async function handleSocialLogin(provider) {
  try {
    const response = await fetch('/api/v1/auth/social', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider: provider,
        token: `mock_oauth_token_${provider}_` + Date.now(),
        fullName: `Usuário ${provider}`,
        email: `user_${provider.toLowerCase()}@fredinvest.com`
      })
    });

    if (!response.ok) throw new Error(`Falha no login com ${provider}`);

    const data = await response.json();
    jwtToken = data.accessToken;
    localStorage.setItem('fred_jwt', jwtToken);
    showAppView();
  } catch (err) {
    alert(err.message);
  }
}

function logout() {
  jwtToken = null;
  localStorage.removeItem('fred_jwt');
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
        <button onclick="deleteAsset(${a.id})" style="background: none; border: none; color: var(--accent-red); cursor: pointer;">🗑️</button>
      </td>
    </tr>
  `).join('');
}

function openAddAssetModal() {
  document.getElementById('add-asset-box').classList.toggle('hidden');
}

async function handleAddAsset(e) {
  e.preventDefault();
  if (!currentPortfolioId) return;

  const ticker = document.getElementById('asset-ticker').value;
  const name = document.getElementById('asset-name').value;
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
      document.getElementById('add-asset-box').classList.add('hidden');
      loadDashboardData();
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
async function runAiAnalysis() {
  const resultDiv = document.getElementById('ai-analysis-result');
  resultDiv.innerHTML = '<p style="color: var(--accent-blue);">Analisando carteira com inteligência artificial...</p>';

  try {
    const res = await fetch('/api/v1/ai/analyze', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${jwtToken}`
      },
      body: JSON.stringify({ riskProfile: 'MODERADO' })
    });

    const data = await res.json();
    resultDiv.innerHTML = `
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
      body: JSON.stringify({ prompt })
    });

    const data = await res.json();
    chatContainer.innerHTML += `<div style="margin-bottom: 0.5rem; color: var(--accent-blue);"><strong>Assistente (${data.timestamp}):</strong> ${data.reply}</div>`;
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


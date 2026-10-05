import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { installStaleChunkReload } from './lib/stale-chunks';
import './styles/index.css';

// After a redeploy, an open tab may ask for page chunks that no longer exist. Record those failures;
// the page error boundary reloads once when one of them keeps a page from rendering.
installStaleChunkReload();

const container = document.getElementById('root');
if (!container) {
  throw new Error('VANTA website: #root element is missing from index.html');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

import { BrowserRouter } from 'react-router';
import { AppRoutes } from './routes';

/** Root component: declarative router around the route table. */
export default function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}

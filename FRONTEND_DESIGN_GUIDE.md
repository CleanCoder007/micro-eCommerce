# E-Commerce Frontend Design & Implementation Guide

## Overview

This document outlines the frontend architecture, design system, and implementation strategy for the microservices e-commerce platform. The design aligns with the backend APIs (Customer, Inventory, Order, Payment services) and follows responsive mobile-first principles.

---

## 📐 Design System

### Color Palette
- **Primary**: `#667eea` (Purple)
- **Secondary**: `#764ba2` (Dark Purple)
- **Success**: `#4caf50` (Green)
- **Warning**: `#ff9800` (Orange)
- **Error**: `#d32f2f` (Red)
- **Neutral**: `#f5f5f5` - `#1a1a1a` (Light to Dark)

### Typography
- **Font Family**: `-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`
- **Heading**: 600-700 weight
- **Body**: 400 weight
- **Caption**: 12px, color: `#999`

### Spacing
- Base unit: 4px
- Common: 8px, 12px, 16px, 20px, 24px, 32px

### Border Radius
- Small: 4px
- Medium: 6px
- Large: 8px
- Full: 50% (circles)

---

## 🏗️ Frontend Architecture

### Project Structure

```
frontend/
├── src/
│   ├── components/
│   │   ├── Auth/
│   │   │   ├── LoginForm.tsx
│   │   │   ├── LoginPage.tsx
│   │   │   └── ProtectedRoute.tsx
│   │   ├── Products/
│   │   │   ├── ProductCatalog.tsx
│   │   │   ├── ProductCard.tsx
│   │   │   ├── ProductFilter.tsx
│   │   │   └── ProductSearch.tsx
│   │   ├── Cart/
│   │   │   ├── ShoppingCart.tsx
│   │   │   ├── CartItem.tsx
│   │   │   └── CartSummary.tsx
│   │   ├── Checkout/
│   │   │   ├── CheckoutFlow.tsx
│   │   │   ├── PaymentForm.tsx
│   │   │   ├── ShippingForm.tsx
│   │   │   └── ProgressIndicator.tsx
│   │   ├── Orders/
│   │   │   ├── OrderList.tsx
│   │   │   ├── OrderCard.tsx
│   │   │   ├── OrderTracking.tsx
│   │   │   └── StatusTimeline.tsx
│   │   ├── Common/
│   │   │   ├── Header.tsx
│   │   │   ├── Navigation.tsx
│   │   │   ├── Footer.tsx
│   │   │   ├── Button.tsx
│   │   │   ├── Input.tsx
│   │   │   ├── Modal.tsx
│   │   │   └── LoadingSpinner.tsx
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── HomePage.tsx
│   │   ├── ProductsPage.tsx
│   │   ├── CartPage.tsx
│   │   ├── CheckoutPage.tsx
│   │   ├── OrdersPage.tsx
│   │   └── NotFoundPage.tsx
│   ├── services/
│   │   ├── api.ts (Axios instance)
│   │   ├── authService.ts
│   │   ├── customerService.ts
│   │   ├── inventoryService.ts
│   │   ├── orderService.ts
│   │   └── paymentService.ts
│   ├── hooks/
│   │   ├── useAuth.ts
│   │   ├── useCart.ts
│   │   ├── useProducts.ts
│   │   ├── useOrders.ts
│   │   └── useApi.ts
│   ├── context/
│   │   ├── AuthContext.tsx
│   │   ├── CartContext.tsx
│   │   └── ThemeContext.tsx
│   ├── types/
│   │   ├── api.ts
│   │   ├── auth.ts
│   │   ├── product.ts
│   │   ├── order.ts
│   │   └── payment.ts
│   ├── styles/
│   │   ├── global.css
│   │   ├── variables.css
│   │   └── responsive.css
│   ├── utils/
│   │   ├── validation.ts
│   │   ├── formatting.ts
│   │   ├── storage.ts
│   │   └── errorHandler.ts
│   ├── App.tsx
│   └── index.tsx
├── public/
├── package.json
└── tsconfig.json
```

---

## 🔌 API Integration

### Base Configuration

```typescript
// services/api.ts
import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add auth token
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('authToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor for error handling
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Handle unauthorized - redirect to login
      localStorage.removeItem('authToken');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

### Service Endpoints

#### Authentication Service
```typescript
// services/authService.ts
export const authService = {
  login: (username: string, password: string) =>
    apiClient.post('/auth/login', { username, password }),
  
  getCurrentUser: () =>
    apiClient.get('/auth/me'),
  
  logout: () => {
    localStorage.removeItem('authToken');
  }
};
```

#### Customer Service
```typescript
// services/customerService.ts
export const customerService = {
  getCustomers: (page = 0, size = 20) =>
    apiClient.get('/customers', { params: { page, size } }),
  
  getCustomerById: (id: string | number) =>
    apiClient.get(`/customers/${id}`),
  
  createCustomer: (data: CustomerData) =>
    apiClient.post('/customers', data),
  
  updateCustomer: (id: string | number, data: CustomerData) =>
    apiClient.put(`/customers/${id}`, data),
  
  deleteCustomer: (id: string | number) =>
    apiClient.delete(`/customers/${id}`)
};
```

#### Inventory Service
```typescript
// services/inventoryService.ts
export const inventoryService = {
  getProducts: (page = 0, size = 20) =>
    apiClient.get('/inventory', { params: { page, size } }),
  
  getProductById: (id: string | number) =>
    apiClient.get(`/inventory/${id}`),
  
  searchProducts: (query: string) =>
    apiClient.get('/inventory', { params: { search: query } }),
  
  reserveStock: (productId: string | number, quantity: number) =>
    apiClient.post(`/inventory/${productId}/reserve`, { quantity }),
  
  releaseStock: (productId: string | number, quantity: number) =>
    apiClient.post(`/inventory/${productId}/release`, { quantity })
};
```

#### Order Service
```typescript
// services/orderService.ts
export const orderService = {
  getOrders: (page = 0, size = 10) =>
    apiClient.get('/orders', { params: { page, size } }),
  
  getOrderById: (id: string | number) =>
    apiClient.get(`/orders/${id}`),
  
  createOrder: (data: OrderData) =>
    apiClient.post('/orders', data),
  
  updateOrderStatus: (id: string | number, status: string) =>
    apiClient.put(`/orders/${id}/status`, { status }),
  
  cancelOrder: (id: string | number) =>
    apiClient.put(`/orders/${id}/cancel`, {})
};
```

#### Payment Service
```typescript
// services/paymentService.ts
export const paymentService = {
  getPayments: (page = 0, size = 20) =>
    apiClient.get('/payments', { params: { page, size } }),
  
  processPayment: (data: PaymentData) =>
    apiClient.post('/payments', data),
  
  refundPayment: (paymentId: string | number) =>
    apiClient.post(`/payments/${paymentId}/refund`, {})
};
```

---

## 🎣 Custom Hooks

### useAuth Hook
```typescript
// hooks/useAuth.ts
import { useState, useContext, useCallback } from 'react';
import { AuthContext } from '../context/AuthContext';
import { authService } from '../services/authService';

export const useAuth = () => {
  const context = useContext(AuthContext);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const login = useCallback(async (username: string, password: string) => {
    setLoading(true);
    setError(null);
    try {
      const response = await authService.login(username, password);
      localStorage.setItem('authToken', response.data.token);
      context.setUser(response.data.user);
      return true;
    } catch (err: any) {
      setError(err.response?.data?.message || 'Login failed');
      return false;
    } finally {
      setLoading(false);
    }
  }, [context]);

  return { ...context, login, loading, error };
};
```

### useCart Hook
```typescript
// hooks/useCart.ts
import { useState, useCallback } from 'react';
import { inventoryService } from '../services/inventoryService';

export const useCart = () => {
  const [cart, setCart] = useState<CartItem[]>([]);
  const [loading, setLoading] = useState(false);

  const addToCart = useCallback(async (productId: number, quantity: number) => {
    setLoading(true);
    try {
      // Reserve stock
      await inventoryService.reserveStock(productId, quantity);
      
      // Add to cart state
      setCart(prev => {
        const existing = prev.find(item => item.productId === productId);
        if (existing) {
          return prev.map(item =>
            item.productId === productId
              ? { ...item, quantity: item.quantity + quantity }
              : item
          );
        }
        return [...prev, { productId, quantity }];
      });
    } finally {
      setLoading(false);
    }
  }, []);

  const removeFromCart = useCallback(async (productId: number) => {
    const item = cart.find(i => i.productId === productId);
    if (item) {
      await inventoryService.releaseStock(productId, item.quantity);
      setCart(prev => prev.filter(i => i.productId !== productId));
    }
  }, [cart]);

  return { cart, addToCart, removeFromCart, loading };
};
```

### useApi Hook (Generic)
```typescript
// hooks/useApi.ts
import { useState, useEffect } from 'react';
import { AxiosError, AxiosRequestConfig } from 'axios';
import { apiClient } from '../services/api';

interface UseApiState<T> {
  data: T | null;
  loading: boolean;
  error: AxiosError | null;
}

export const useApi = <T,>(
  url: string,
  config?: AxiosRequestConfig
): UseApiState<T> => {
  const [state, setState] = useState<UseApiState<T>>({
    data: null,
    loading: true,
    error: null,
  });

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await apiClient.get<T>(url, config);
        setState({ data: response.data, loading: false, error: null });
      } catch (error) {
        setState({ data: null, loading: false, error: error as AxiosError });
      }
    };

    fetchData();
  }, [url, config]);

  return state;
};
```

---

## 🔐 Context & State Management

### Auth Context
```typescript
// context/AuthContext.tsx
import React, { createContext, useState } from 'react';

interface User {
  id: number;
  username: string;
  email: string;
  roles: string[];
}

export const AuthContext = createContext<{
  user: User | null;
  setUser: (user: User | null) => void;
  isAuthenticated: boolean;
}>({
  user: null,
  setUser: () => {},
  isAuthenticated: false,
});

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [user, setUser] = useState<User | null>(
    () => {
      const stored = localStorage.getItem('user');
      return stored ? JSON.parse(stored) : null;
    }
  );

  const handleSetUser = (newUser: User | null) => {
    setUser(newUser);
    if (newUser) {
      localStorage.setItem('user', JSON.stringify(newUser));
    } else {
      localStorage.removeItem('user');
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        setUser: handleSetUser,
        isAuthenticated: !!user,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
```

### Cart Context
```typescript
// context/CartContext.tsx
import React, { createContext, useState } from 'react';

export interface CartItem {
  productId: number;
  quantity: number;
  price?: number;
  name?: string;
}

export const CartContext = createContext<{
  items: CartItem[];
  addItem: (item: CartItem) => void;
  removeItem: (productId: number) => void;
  clearCart: () => void;
  updateQuantity: (productId: number, quantity: number) => void;
}>({
  items: [],
  addItem: () => {},
  removeItem: () => {},
  clearCart: () => {},
  updateQuantity: () => {},
});

export const CartProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [items, setItems] = useState<CartItem[]>(
    () => {
      const stored = localStorage.getItem('cart');
      return stored ? JSON.parse(stored) : [];
    }
  );

  const addItem = (item: CartItem) => {
    setItems(prev => {
      const existing = prev.find(i => i.productId === item.productId);
      const updated = existing
        ? prev.map(i =>
            i.productId === item.productId
              ? { ...i, quantity: i.quantity + item.quantity }
              : i
          )
        : [...prev, item];
      localStorage.setItem('cart', JSON.stringify(updated));
      return updated;
    });
  };

  const removeItem = (productId: number) => {
    setItems(prev => {
      const updated = prev.filter(i => i.productId !== productId);
      localStorage.setItem('cart', JSON.stringify(updated));
      return updated;
    });
  };

  const clearCart = () => {
    setItems([]);
    localStorage.removeItem('cart');
  };

  const updateQuantity = (productId: number, quantity: number) => {
    setItems(prev => {
      const updated = prev.map(i =>
        i.productId === productId ? { ...i, quantity } : i
      );
      localStorage.setItem('cart', JSON.stringify(updated));
      return updated;
    });
  };

  return (
    <CartContext.Provider
      value={{ items, addItem, removeItem, clearCart, updateQuantity }}
    >
      {children}
    </CartContext.Provider>
  );
};
```

---

## 📱 Responsive Design Breakpoints

```css
/* Mobile First Approach */
/* Mobile: 390px - 600px */
@media (max-width: 600px) {
  /* Mobile-optimized styles */
  .grid { grid-template-columns: 1fr; }
}

/* Tablet: 601px - 1024px */
@media (min-width: 601px) and (max-width: 1024px) {
  /* Tablet-optimized styles */
  .grid { grid-template-columns: repeat(2, 1fr); }
}

/* Desktop: 1025px+ */
@media (min-width: 1025px) {
  /* Desktop-optimized styles */
  .grid { grid-template-columns: repeat(3, 1fr); }
}
```

---

## 🔄 Data Flow

### Login Flow
1. User enters credentials
2. LoginForm calls `authService.login()`
3. API returns token and user data
4. Store token in localStorage and user in AuthContext
5. Redirect to dashboard
6. Add token to all subsequent requests via interceptor

### Shopping Flow
1. Browse products via ProductCatalog
2. Click "Add to Cart" → calls `useCart.addToCart()`
3. Hook calls `inventoryService.reserveStock()`
4. Item added to CartContext and localStorage
5. Navigate to cart and review items
6. Click checkout → Navigate to CheckoutPage
7. Fill payment details
8. Call `orderService.createOrder()`
9. Call `paymentService.processPayment()`
10. On success, clear cart and redirect to orders page

### Order Tracking Flow
1. Navigate to orders page
2. Fetch orders via `useApi('/orders')`
3. Display OrderCard for each order
4. Click "Track" to see status timeline
5. Status updates via polling or WebSocket (optional)

---

## ✅ Error Handling

### Global Error Handler
```typescript
// utils/errorHandler.ts
export const handleApiError = (error: any): string => {
  if (error.response?.data?.message) {
    return error.response.data.message;
  }
  if (error.response?.status === 401) {
    return 'Unauthorized. Please login again.';
  }
  if (error.response?.status === 404) {
    return 'Resource not found.';
  }
  if (error.response?.status === 429) {
    return 'Too many requests. Please try again later.';
  }
  return error.message || 'An error occurred';
};
```

### Error Boundary Component
```typescript
// components/Common/ErrorBoundary.tsx
import React from 'react';

export class ErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { hasError: boolean; error: Error | null }
> {
  constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: '20px', textAlign: 'center' }}>
          <h2>Something went wrong</h2>
          <p>{this.state.error?.message}</p>
        </div>
      );
    }

    return this.props.children;
  }
}
```

---

## 🚀 Performance Optimization

### Code Splitting
```typescript
// App.tsx
import { lazy, Suspense } from 'react';

const LoginPage = lazy(() => import('./pages/LoginPage'));
const ProductsPage = lazy(() => import('./pages/ProductsPage'));
const CartPage = lazy(() => import('./pages/CartPage'));

export const App = () => (
  <Suspense fallback={<LoadingSpinner />}>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/products" element={<ProductsPage />} />
      <Route path="/cart" element={<CartPage />} />
    </Routes>
  </Suspense>
);
```

### Memoization
```typescript
// components/Products/ProductCard.tsx
import { memo } from 'react';

const ProductCard = memo(({ product, onAddToCart }: Props) => {
  return (
    <div className="product-card">
      <h3>{product.name}</h3>
      <p>${product.price}</p>
      <button onClick={() => onAddToCart(product.id)}>Add to Cart</button>
    </div>
  );
});
```

---

## 🧪 Testing Strategy

### Unit Tests
- Test utility functions and formatters
- Mock API calls with MSW (Mock Service Worker)
- Test context providers and custom hooks

### Integration Tests
- Test component interactions
- Test form submissions
- Test API integration flows

### E2E Tests
- Test complete user journeys (login → purchase → order tracking)
- Use Cypress or Playwright

---

## 📦 Dependencies

### Required
```json
{
  "react": "^18.x",
  "react-dom": "^18.x",
  "react-router-dom": "^6.x",
  "axios": "^1.x"
}
```

### Recommended
```json
{
  "typescript": "^5.x",
  "@react-icons/all-files": "^4.x",
  "date-fns": "^2.x",
  "zustand": "^4.x" // Alternative state management
}
```

---

## 🔧 Environment Variables

```env
REACT_APP_API_URL=http://localhost:8080/api
REACT_APP_ENVIRONMENT=development
REACT_APP_LOG_LEVEL=debug
REACT_APP_ENABLE_ANALYTICS=false
```

---

## 📝 Component Examples

### LoginForm Component
```typescript
// components/Auth/LoginForm.tsx
import React, { useState } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { useNavigate } from 'react-router-dom';

export const LoginForm: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const { login, loading, error } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const success = await login(username, password);
    if (success) {
      navigate('/products');
    }
  };

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div>
        <label>Username</label>
        <input
          type="text"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
        />
      </div>
      <div>
        <label>Password</label>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
      </div>
      {error && <div style={{ color: '#d32f2f' }}>{error}</div>}
      <button type="submit" disabled={loading}>
        {loading ? 'Signing in...' : 'Sign In'}
      </button>
    </form>
  );
};
```

---

## 🚀 Deployment Checklist

- [ ] Build optimization (`npm run build`)
- [ ] Environment variables configured
- [ ] API endpoint pointing to production
- [ ] Error tracking setup (Sentry)
- [ ] Analytics configured
- [ ] Security headers set
- [ ] CORS properly configured
- [ ] Tests passing (unit, integration, e2e)
- [ ] Performance audit passed
- [ ] Accessibility audit passed

---

## 📚 Additional Resources

- [React Documentation](https://react.dev)
- [Axios Documentation](https://axios-http.com)
- [React Router Documentation](https://reactrouter.com)
- [TypeScript Handbook](https://www.typescriptlang.org/docs/)
- [Microservices Patterns](https://microservices.io)

---

**Last Updated**: 2026-09-27

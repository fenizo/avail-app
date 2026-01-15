import './globals.css'
import type { Metadata } from 'next'

export const metadata: Metadata = {
  title: 'MEP Admin Panel',
  description: 'Field Service & Billing Management',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body>
        <div className="sidebar">
          <h1 className="gradient-text" style={{ fontSize: '1.5rem', marginBottom: '32px' }}>MEP Pro</h1>
          <nav style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <a href="/" style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>Dashboard</a>
            <a href="/jobs" style={{ color: '#94a3b8', textDecoration: 'none' }}>Jobs</a>
            <a href="/call-logs" style={{ color: '#94a3b8', textDecoration: 'none' }}>Call Logs</a>
            <a href="/customers" style={{ color: '#94a3b8', textDecoration: 'none' }}>Customers</a>
            <a href="/staff" style={{ color: '#94a3b8', textDecoration: 'none' }}>Staff</a>
            <a href="/settings" style={{ color: '#94a3b8', textDecoration: 'none' }}>Settings</a>
          </nav>
        </div>
        <main className="main-content">
          {children}
        </main>
      </body>
    </html>
  )
}

import { useEffect, useState } from 'react'
import { getPeerApiBase } from './settings'
import StatusHeader from './components/StatusHeader'
import SearchTab from './components/SearchTab'
import DownloadsTab from './components/DownloadsTab'
import LibraryTab from './components/LibraryTab'

const TABS = [
  { id: 'search', label: 'Pretraga' },
  { id: 'downloads', label: 'Preuzimanja' },
  { id: 'library', label: 'Biblioteka' },
]

function App() {
  const [activeTab, setActiveTab] = useState('search')

  useEffect(() => {
    console.info('[P2P GUI] started. Selected local peer API:', getPeerApiBase())
  }, [])

  return (
    <>
      <div className="blob-field">
        <div className="blob blob--a" />
        <div className="blob blob--b" />
        <div className="blob blob--c" />
      </div>

      <div className="app-shell">
        <StatusHeader />

        <nav className="tab-nav">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              className={`tab-btn ${activeTab === tab.id ? 'active' : ''}`}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </nav>

        {activeTab === 'search' && <SearchTab onDownloadStarted={() => setActiveTab('downloads')} />}
        {activeTab === 'downloads' && <DownloadsTab />}
        {activeTab === 'library' && <LibraryTab />}
      </div>
    </>
  )
}

export default App

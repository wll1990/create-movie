import { NavLink } from 'react-router-dom';
import { Film, Home, PlusCircle } from 'lucide-react';

const navItems = [
  { to: '/', icon: Home, label: '项目列表' },
  { to: '/create', icon: PlusCircle, label: '新建项目' },
];

export default function Sidebar() {
  return (
    <aside className="w-56 bg-gray-900 border-r border-gray-800 flex flex-col">
      <div className="p-4 border-b border-gray-800">
        <div className="flex items-center gap-2">
          <Film className="w-6 h-6 text-blue-400" />
          <span className="font-bold text-lg">MakeMovie</span>
        </div>
        <p className="text-xs text-gray-500 mt-1">漫剧创作平台</p>
      </div>

      <nav className="flex-1 p-3 space-y-1">
        {navItems.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors ${
                isActive
                  ? 'bg-blue-600 text-white font-medium'
                  : 'text-gray-400 hover:bg-gray-800 hover:text-white'
              }`
            }
          >
            <Icon className="w-4 h-4" />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="p-4 border-t border-gray-800 text-xs text-gray-600">
        MakeMovie v0.1
      </div>
    </aside>
  );
}

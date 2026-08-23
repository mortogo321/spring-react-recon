import { useMemo, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Container from '@mui/material/Container';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Toolbar from '@mui/material/Toolbar';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useColorScheme } from '@mui/material/styles';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import LogoutIcon from '@mui/icons-material/Logout';
import MenuIcon from '@mui/icons-material/Menu';
import SpaceDashboardIcon from '@mui/icons-material/SpaceDashboard';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutlined';
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined';
import StorefrontIcon from '@mui/icons-material/Storefront';

import { useAppDispatch, useAppSelector } from '../app/hooks';
import { selectUser, signedOut } from '../features/auth/authSlice';
import { humanise } from '../format';

const NAV = [
  { to: '/', label: 'Dashboard', icon: <SpaceDashboardIcon fontSize="small" />, end: true },
  { to: '/runs', label: 'Runs', icon: <PlayCircleOutlineIcon fontSize="small" />, end: false },
  { to: '/exceptions', label: 'Exceptions', icon: <ReportProblemOutlinedIcon fontSize="small" />, end: false },
  { to: '/merchants', label: 'Merchants', icon: <StorefrontIcon fontSize="small" />, end: false },
] as const;

const DRAWER_WIDTH = 232;

/**
 * The shell: navigation, who is signed in and what they are allowed to do, and the colour scheme.
 *
 * The role chips are not decoration. This console enforces segregation of duties, so the first
 * question a user has when a button is missing is "which hat am I wearing" — showing the roles
 * next to the name answers it without a support ticket.
 */
export function AppShell() {
  const user = useAppSelector(selectUser);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { mode, setMode } = useColorScheme();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);

  const initials = useMemo(() => {
    const name = user?.displayName ?? user?.username ?? '';
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('');
  }, [user]);

  function signOut() {
    setMenuAnchor(null);
    dispatch(signedOut());
    navigate('/login', { replace: true });
  }

  const nav = (
    // A landmark, not a bare list: this is the primary navigation, and screen-reader users skip to
    // it by role. It also gives tests a way to mean "the sidebar Runs link" rather than any link
    // on the page whose text contains the word.
    <List component="nav" aria-label="Main" dense sx={{ pt: 1 }}>
      {NAV.map((item) => (
        <ListItemButton
          key={item.to}
          component={NavLink}
          to={item.to}
          end={item.end}
          onClick={() => setDrawerOpen(false)}
          sx={{
            mx: 1,
            borderRadius: 1,
            '&.active': { bgcolor: 'action.selected', fontWeight: 600 },
          }}
        >
          <ListItemIcon sx={{ minWidth: 34 }}>{item.icon}</ListItemIcon>
          <ListItemText primary={item.label} />
        </ListItemButton>
      ))}
    </List>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100dvh', bgcolor: 'background.default' }}>
      <AppBar position="fixed" color="primary" enableColorOnDark sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar variant="dense">
          <IconButton
            edge="start"
            color="inherit"
            aria-label="Open navigation"
            onClick={() => setDrawerOpen(true)}
            sx={{ mr: 1, display: { md: 'none' } }}
          >
            <MenuIcon />
          </IconButton>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, flexGrow: 1 }}>
            Settlement Reconciliation
          </Typography>

          <Tooltip title={mode === 'dark' ? 'Switch to light' : 'Switch to dark'}>
            <IconButton color="inherit" onClick={() => setMode(mode === 'dark' ? 'light' : 'dark')} aria-label="Toggle colour scheme">
              {mode === 'dark' ? <LightModeIcon fontSize="small" /> : <DarkModeIcon fontSize="small" />}
            </IconButton>
          </Tooltip>

          <Tooltip title={user?.displayName ?? ''}>
            <Chip
              onClick={(event) => setMenuAnchor(event.currentTarget)}
              label={initials || '?'}
              size="small"
              sx={{ ml: 1, fontWeight: 700, cursor: 'pointer', bgcolor: 'rgba(255,255,255,0.18)', color: 'inherit' }}
            />
          </Tooltip>
          <Menu anchorEl={menuAnchor} open={menuAnchor !== null} onClose={() => setMenuAnchor(null)}>
            <Box sx={{ px: 2, py: 1 }}>
              <Typography variant="subtitle2">{user?.displayName}</Typography>
              <Typography variant="caption" color="text.secondary">
                {user?.username}
              </Typography>
              <Stack direction="row" spacing={0.5} sx={{ mt: 1, flexWrap: 'wrap', gap: 0.5 }}>
                {(user?.roles ?? []).map((role) => (
                  <Chip key={role} size="small" variant="outlined" label={humanise(role)} />
                ))}
              </Stack>
            </Box>
            <Divider sx={{ my: 0.5 }} />
            <MenuItem onClick={signOut}>
              <ListItemIcon>
                <LogoutIcon fontSize="small" />
              </ListItemIcon>
              Sign out
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <Drawer
        variant="temporary"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        ModalProps={{ keepMounted: true }}
        sx={{ display: { md: 'none' }, '& .MuiDrawer-paper': { width: DRAWER_WIDTH } }}
      >
        <Toolbar variant="dense" />
        {nav}
      </Drawer>
      <Drawer
        variant="permanent"
        sx={{
          display: { xs: 'none', md: 'block' },
          width: DRAWER_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
        }}
      >
        <Toolbar variant="dense" />
        {nav}
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, minWidth: 0 }}>
        <Toolbar variant="dense" />
        <Container maxWidth="xl" sx={{ py: 3 }}>
          <Outlet />
        </Container>
      </Box>
    </Box>
  );
}

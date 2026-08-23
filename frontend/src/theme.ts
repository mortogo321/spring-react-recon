import { createTheme, type Theme } from '@mui/material/styles';

/**
 * One theme, two colour schemes. MUI 9's `colorSchemes` puts light and dark behind CSS variables,
 * so switching is a class change on <html> rather than a re-render of the whole tree — which
 * matters on a page holding a 500-row grid.
 *
 * The palette is chosen for the job rather than for decoration: an analyst reads this screen for
 * hours, and severity has to be legible at a glance without relying on colour alone (every
 * severity and state chip also carries its own label, and the grid encodes exposure in a monospace
 * right-aligned column).
 */
export const theme: Theme = createTheme({
  cssVariables: { colorSchemeSelector: 'class' },
  colorSchemes: {
    light: {
      palette: {
        primary: { main: '#1b4965' },
        secondary: { main: '#5fa8d3' },
        success: { main: '#2e7d32' },
        warning: { main: '#b26a00' },
        error: { main: '#b3261e' },
        background: { default: '#f4f6f8', paper: '#ffffff' },
      },
    },
    dark: {
      palette: {
        primary: { main: '#7fb2d0' },
        secondary: { main: '#5fa8d3' },
        success: { main: '#7bc67e' },
        warning: { main: '#e3a008' },
        error: { main: '#f2837a' },
        background: { default: '#0e1116', paper: '#161b22' },
      },
    },
  },
  shape: { borderRadius: 8 },
  typography: {
    fontFamily: ['"Inter"', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'].join(','),
    h5: { fontWeight: 600 },
    h6: { fontWeight: 600 },
    // Amounts and identifiers are read by comparing digit positions between rows, which only
    // works in a tabular font.
    body2: { fontVariantNumeric: 'tabular-nums' },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        // Scrollbars appear and disappear as the grid pages; without this the whole layout
        // shifts by the scrollbar width on every page change.
        html: { scrollbarGutter: 'stable' },
      },
    },
    MuiCard: { defaultProps: { variant: 'outlined' } },
    MuiPaper: { defaultProps: { elevation: 0 } },
    MuiButton: { defaultProps: { disableElevation: true } },
    MuiTextField: { defaultProps: { size: 'small' } },
    MuiTableCell: { styleOverrides: { root: { fontVariantNumeric: 'tabular-nums' } } },
  },
});

export const MONOSPACE = '"JetBrains Mono", "SF Mono", ui-monospace, monospace';

import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';

import { MONOSPACE } from '../theme';

interface KpiCardProps {
  label: string;
  value: string;
  /** One line saying what the number means, because "exposure" is not self-explanatory. */
  help?: string;
  caption?: string;
  tone?: 'default' | 'good' | 'warn' | 'bad';
}

const TONE_COLOUR = {
  default: 'text.primary',
  good: 'success.main',
  warn: 'warning.main',
  bad: 'error.main',
} as const;

export function KpiCard({ label, value, help, caption, tone = 'default' }: KpiCardProps) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" spacing={0.5} sx={{ alignItems: "center" }}>
          <Typography variant="overline" color="text.secondary" noWrap>
            {label}
          </Typography>
          {help && (
            <Tooltip title={help}>
              <InfoOutlinedIcon sx={{ fontSize: 14, color: 'text.disabled' }} />
            </Tooltip>
          )}
        </Stack>
        <Typography
          variant="h5"
          sx={{ mt: 0.5, fontFamily: MONOSPACE, color: TONE_COLOUR[tone], wordBreak: 'break-word' }}
        >
          {value}
        </Typography>
        {caption && (
          <Typography variant="caption" color="text.secondary">
            {caption}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

# Configuration

StatusMod stores its local settings in:

`config/statusmod/config.json`

## Important fields

- `adminOpLevel`
- `statusPermissionNode`
- `adminPermissionNode`
- `enableAdminOverrides`
- `defaultColor`
- `statusReapplyTicks`
- `statusCooldownSeconds`
- `statusHistorySize`
- `enableStaffBadge`
- `staffBadgeText`
- `staffBadgeColor`

## Storage files

- `config/statusmod/players.json`
- `config/statusmod/blocked_players.json`
- `config/statusmod/config.json`

## Behavior

- Atomic file writes help reduce corruption risk.
- Invalid input is sanitized where possible.
- Malformed JSON is recovered with backups so the mod keeps running.

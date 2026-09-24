import { registerGaugeZoneCard } from './gauge-zone-card.js';
import { registerAlarmPanelCard } from './alarm-panel-card.js';
import { registerLightControlCard } from './light-control-card.js';

registerGaugeZoneCard();
registerAlarmPanelCard();
registerLightControlCard();

console.info('%c CHURCH-DRIVE-CARDS %c loaded ', 'color: white; background: #2196f3; font-weight: 700;', 'color: #2196f3; background: transparent;');

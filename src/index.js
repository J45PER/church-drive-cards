import { registerGaugeZoneCard } from './gauge-zone-card.js';
import { registerAlarmPanelCard } from './alarm-panel-card.js';
import { registerLightControlCard } from './light-control-card.js';
import { registerSceneStylesCard } from './scene-styles-card.js';
import { registerSceneBuilderCard } from './scene-builder-card.js';
import { registerSectionTitleCard } from './section-title-card.js';
import { registerSectionPanelCard } from './section-panel-card.js';
import { registerClimateCard } from './climate-card.js';
import { SUFFIX } from './suffix.js';

registerGaugeZoneCard();
registerAlarmPanelCard();
registerLightControlCard();
registerSceneStylesCard();
registerSceneBuilderCard();
registerSectionTitleCard();
registerSectionPanelCard();
registerClimateCard();

console.info(`%c CHURCH-DRIVE-CARDS${SUFFIX ? ' BETA' : ''} %c loaded `, 'color: white; background: #2196f3; font-weight: 700;', 'color: #2196f3; background: transparent;');

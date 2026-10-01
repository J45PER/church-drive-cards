"""Constants for Church Drive."""

DOMAIN = "church_drive"

# The cards bundle shipped in ./frontend, served by Home Assistant and loaded
# on every dashboard page (no Lovelace resource needed).
CARDS_FILE = "church-drive-cards.js"
URL_BASE = "/church_drive"

# Options: the Hue rooms/zones (bridge group ids) the universal scenes sync to.
CONF_SCENE_GROUPS = "scene_groups"

SERVICE_SYNC_SCENES = "sync_scenes"
SERVICE_APPLY_SCENE = "apply_scene"
WS_LIBRARY = "church_drive/library"
WS_SCENE_SAVE = "church_drive/scene/save"
WS_SCENE_DELETE = "church_drive/scene/delete"
WS_SCENE_PREVIEW = "church_drive/scene/preview"

# Dispatcher signals: a scene was applied; the library changed.
SIGNAL_ACTIVE = "church_drive_active"
SIGNAL_LIBRARY = "church_drive_library"

# Device health: watched entities (options), the fix action, the update signal.
CONF_HEALTH_ENTITIES = "health_entities"
SERVICE_HEALTH_FIX = "health_fix"
SIGNAL_HEALTH = "church_drive_health"

# People and notifications.
SERVICE_NOTIFY = "notify"
WS_PEOPLE = "church_drive/people"
WS_PEOPLE_ASSIGN = "church_drive/people/assign"
WS_PEOPLE_PHONE = "church_drive/people/phone"

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
WS_PEOPLE_PLACES = "church_drive/people/places"

# Mode icons (icons.py): read by the cards and the app, changed in the Icon Styles card.
WS_ICONS = "church_drive/icons"
WS_ICON_SET = "church_drive/icons/set"
EVENT_ICONS = "church_drive_icons_changed"

# Camera events (events.py).
WS_CAMERA_EVENTS = "church_drive/camera/events"
WS_CAMERA_SETTINGS = "church_drive/camera/settings"
WS_CAMERA_LINKS = "church_drive/camera/links"
WS_CAMERA_LINK_SET = "church_drive/camera/links/set"

# Google maps (options): the Map Tiles key goes to browsers (lock it to Home
# Assistant's addresses); the Places key stays here, for searching.
CONF_MAPS_TILES_KEY = "google_tiles_key"
CONF_MAPS_PLACES_KEY = "google_places_key"
WS_MAPS = "church_drive/maps"
WS_MAPS_SEARCH = "church_drive/maps/search"

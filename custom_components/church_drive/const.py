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
EVENT_NOTICE_TEST = "church_drive_notice_test"  # fired by the Church Drive app's "Send me a test"
WS_PEOPLE = "church_drive/people"
WS_PEOPLE_ASSIGN = "church_drive/people/assign"
WS_PEOPLE_MINE = "church_drive/people/mine"
WS_NOTICES_MISSED = "church_drive/notices/missed"
WS_PEOPLE_PHONE = "church_drive/people/phone"
WS_PEOPLE_PLACES = "church_drive/people/places"
WS_PEOPLE_CARS = "church_drive/people/cars"
WS_PEOPLE_ACCESS = "church_drive/people/access"
WS_PEOPLE_TRACKING = "church_drive/people/tracking"
WS_PEOPLE_SETTINGS = "church_drive/people/settings"

# Mode icons (icons.py): read by the cards and the app, changed in the Icon Styles card.
WS_ICONS = "church_drive/icons"
WS_ICON_SET = "church_drive/icons/set"
EVENT_ICONS = "church_drive_icons_changed"

# Camera events (events.py).
WS_CAMERA_EVENTS = "church_drive/camera/events"
WS_CAMERA_SETTINGS = "church_drive/camera/settings"
WS_CAMERA_LINKS = "church_drive/camera/links"
WS_CAMERA_LINK_SET = "church_drive/camera/links/set"
WS_CAMERA_ALERT_SET = "church_drive/camera/alerts/set"

# Google maps (options): the Map Tiles key goes to browsers (lock it to Home
# Assistant's addresses); the Places key stays here, for searching.
CONF_MAPS_TILES_KEY = "google_tiles_key"
CONF_MAPS_PLACES_KEY = "google_places_key"
CONF_ASSISTANT_NAME = "assistant_name"
# What the voice assistant is called until the house sets a name (Settings > Devices & services > Church Drive > Configure > Assistant name).
DEFAULT_ASSISTANT_NAME = "Clive"
WS_MAPS = "church_drive/maps"
WS_MAPS_SEARCH = "church_drive/maps/search"
WS_ASSIST_INFO = "church_drive/assist/info"

# Task inbox (inbox.py): text in, checked tasks out to the to-do lists.
SERVICE_ADD_TASKS = "add_tasks"
WS_INBOX = "church_drive/inbox"
WS_INBOX_SUBMIT = "church_drive/inbox/submit"
WS_INBOX_CONFIRM = "church_drive/inbox/confirm"
WS_INBOX_DISMISS = "church_drive/inbox/dismiss"

# Place reminders and snoozing (reminders.py).
WS_REMINDERS = "church_drive/reminders"
WS_REMINDER_SET = "church_drive/reminders/set"

# Shopping lists (shopping.py): one task per list, items bought or unavailable.
SERVICE_SHOPPING_ADD = "shopping_add"
SERVICE_STEPS_CREATE = "steps_create"
WS_SHOPPING = "church_drive/shopping"
WS_SHOPPING_CREATE = "church_drive/shopping/create"
WS_SHOPPING_UPDATE = "church_drive/shopping/update"
WS_SHOPPING_ADD = "church_drive/shopping/add"
WS_SHOPPING_ITEM = "church_drive/shopping/item"
WS_SHOPPING_DELETE = "church_drive/shopping/delete"
WS_SHOPPING_IMPORT = "church_drive/shopping/import"

# What the lights do when an alarm goes off (alarm_lights.py).
WS_ALARM_LIGHTS = "church_drive/alarm_lights"
WS_ALARM_LIGHTS_SET = "church_drive/alarm_lights/set"
WS_ALARM_LIGHTS_TEST = "church_drive/alarm_lights/test"
WS_ALARM_LIGHTS_STOP = "church_drive/alarm_lights/stop"

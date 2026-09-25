"""Constants for Church Drive."""

DOMAIN = "church_drive"

# The cards bundle shipped in ./frontend, served by Home Assistant and loaded
# on every dashboard page (no Lovelace resource needed).
CARDS_FILE = "church-drive-cards.js"
URL_BASE = "/church_drive"

# Options: the Hue rooms/zones (bridge group ids) the universal scenes sync to.
CONF_SCENE_GROUPS = "scene_groups"

SERVICE_SYNC_SCENES = "sync_scenes"

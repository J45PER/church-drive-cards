// Build flavour. The normal build registers `light-control-card` etc.; the
// beta build (see build.mjs) defines __CARD_SUFFIX__ as "-beta" so it
// registers `light-control-card-beta` etc. and can run alongside the release
// for testing on the Design Presets "Beta" tab.

/* global __CARD_SUFFIX__ */
export const SUFFIX = typeof __CARD_SUFFIX__ !== 'undefined' ? __CARD_SUFFIX__ : '';
export const LABEL = SUFFIX ? ' (beta)' : '';

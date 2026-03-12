import { config } from '@vue/test-utils';

config.global.stubs = {
  'el-config-provider': {
    template: '<div><slot /></div>',
  },
  'router-view': {
    template: '<div data-test="router-view" />',
  },
};

if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

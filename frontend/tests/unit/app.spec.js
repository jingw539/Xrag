import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import App from '../../src/App.vue';

describe('App', () => {
  it('renders router view container', () => {
    const wrapper = mount(App);
    expect(wrapper.find('[data-test="router-view"]').exists()).toBe(true);
  });
});

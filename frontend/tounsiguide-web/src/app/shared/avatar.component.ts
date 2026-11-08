import { Component, computed, input } from '@angular/core';
import { initials } from './format';

@Component({
  selector: 'app-avatar',
  standalone: true,
  template: `<span class="avatar" aria-hidden="true">{{ letters() }}</span>`,
})
export class AvatarComponent {
  readonly name = input.required<string>();
  readonly letters = computed(() => initials(this.name()));
}

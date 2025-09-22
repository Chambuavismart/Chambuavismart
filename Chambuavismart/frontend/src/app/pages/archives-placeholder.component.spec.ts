import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ArchivesPlaceholderComponent } from './archives-placeholder.component';

describe('ArchivesPlaceholderComponent', () => {
  let component: ArchivesPlaceholderComponent;
  let fixture: ComponentFixture<ArchivesPlaceholderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ArchivesPlaceholderComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ArchivesPlaceholderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should render the title', () => {
    const h2 = fixture.debugElement.query(By.css('h2'));
    expect(h2.nativeElement.textContent).toContain('Archives (coming soon)');
  });

  it('should have a disabled Upload CSV button', () => {
    const button = fixture.debugElement.query(By.css('button'));
    expect(button).toBeTruthy();
    expect(button.nativeElement.disabled).toBeTrue();
    expect(button.nativeElement.title).toBe('Backend not yet enabled');
  });
});

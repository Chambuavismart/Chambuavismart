import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import { ArchivesUploadComponent } from './archives-upload.component';
import { MatchUploadService } from '../services/match-upload.service';

class MockMatchUploadService {
  uploadUnifiedText(type: any, league: string, country: string, season: string, text: string, opts: any){
    return of({ success: true, inserted: 10, deleted: 0 });
  }
}

describe('ArchivesUploadComponent (Raw Text Only)', () => {
  let component: ArchivesUploadComponent;
  let fixture: ComponentFixture<ArchivesUploadComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ArchivesUploadComponent],
      providers: [{ provide: MatchUploadService, useClass: MockMatchUploadService }]
    }).compileComponents();

    fixture = TestBed.createComponent(ArchivesUploadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should show Raw Text Upload UI and disable Upload until fields filled', () => {
    const header = fixture.debugElement.query(By.css('h1')).nativeElement as HTMLElement;
    expect(header.textContent).toContain('Raw Text Only');

    // Country should be a select dropdown populated with countries
    const selects = fixture.debugElement.queryAll(By.css('select'));
    const countrySelect = selects.find(s => (s.nativeElement as HTMLSelectElement).querySelectorAll('option').length > 10)!;
    expect(countrySelect).toBeTruthy();
    // It should have many options (world countries)
    const options = countrySelect.nativeElement.querySelectorAll('option');
    expect(options.length).toBeGreaterThan(10);

    const uploadBtn = fixture.debugElement.query(By.css('button.btn.btn-primary'));
    expect(uploadBtn.properties['disabled']).toBeTrue();

    component.rawLeagueName = 'Premier League';
    component.rawCountry = 'England';
    component.rawSeason = '2023/2024';
    component.rawText = '2024-08-12, Team A - Team B, 2-1';
    fixture.detectChanges();

    expect(uploadBtn.properties['disabled']).toBeFalse();
  });

  it('should call uploadUnifiedText on Upload click', () => {
    const svc = TestBed.inject(MatchUploadService);
    const spy = spyOn(svc, 'uploadUnifiedText').and.callThrough();

    component.rawLeagueName = 'La Liga';
    component.rawCountry = 'Spain';
    component.rawSeason = '2023/2024';
    component.rawText = '2024-05-21, A - B, 1-0';
    fixture.detectChanges();

    const uploadBtn = fixture.debugElement.query(By.css('button.btn.btn-primary'));
    uploadBtn.nativeElement.click();

    expect(spy).toHaveBeenCalled();
  });
});

import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of, throwError } from 'rxjs';
import { ArchivesHistoryComponent } from './archives-history.component';
import { ArchivesService, ImportError, ImportRunSummary } from '../services/archives.service';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

class MockArchivesService {
  getImportRuns() {
    const runs: ImportRunSummary[] = [
      { id: 1, status: 'COMPLETED', rowsSuccess: 10, rowsFailed: 0, rowsTotal: 10, provider: 'prov', competitionCode: 'E0', startedAt: '2025-09-01' },
      { id: 2, status: 'COMPLETED', rowsSuccess: 8, rowsFailed: 2, rowsTotal: 10, provider: 'prov', competitionCode: 'E1', startedAt: '2025-09-02' }
    ];
    return of(runs);
  }
  getImportRunErrors(runId: number) {
    const errs: ImportError[] = [
      { id: 11, rowNumber: 5, errorMessage: 'Invalid date', rawData: '...'},
      { id: 12, rowNumber: 7, errorMessage: 'Missing team', rawData: '...'}
    ];
    return of(errs);
  }
  downloadImportFile(runId: number) {
    throw new Error('not mocked');
  }
}

describe('ArchivesHistoryComponent', () => {
  let component: ArchivesHistoryComponent;
  let fixture: ComponentFixture<ArchivesHistoryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ArchivesHistoryComponent],
      providers: [{ provide: ArchivesService, useClass: MockArchivesService }]
    }).compileComponents();

    fixture = TestBed.createComponent(ArchivesHistoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders runs with red badge when rowsFailed > 0', fakeAsync(() => {
    tick();
    fixture.detectChanges();
    const rows = fixture.debugElement.queryAll(By.css('tbody tr'));
    expect(rows.length).toBe(2);
    expect(rows[1].nativeElement.textContent).toContain('failed');
  }));

  it('opens modal and shows errors on "View Errors" click', fakeAsync(() => {
    tick();
    fixture.detectChanges();
    const btn = fixture.debugElement.queryAll(By.css('tbody tr button'))[0];
    btn.nativeElement.click();
    tick();
    fixture.detectChanges();
    const modal = fixture.debugElement.query(By.css('app-archives-errors-modal'));
    expect(modal).toBeTruthy();
    const table = fixture.debugElement.query(By.css('app-archives-errors-modal table'));
    expect(table).toBeTruthy();
  }));
  it('should trigger download with correct filename when download succeeds', fakeAsync(() => {
    // Arrange runs loaded
    tick();
    const svc = TestBed.inject(ArchivesService) as any;
    const blob = new Blob(['a,b'], { type: 'text/csv' });
    const headers = new HttpHeaders({ 'Content-Disposition': 'attachment; filename="hist.csv"' });
    spyOn(svc, 'downloadImportFile').and.returnValue(of(new HttpResponse({ body: blob, headers })));

    // Spy URL and anchor
    spyOn(URL, 'createObjectURL').and.returnValue('blob:mock');
    spyOn(URL, 'revokeObjectURL');
    const fakeAnchor: any = { click: jasmine.createSpy('click') };
    spyOn(document, 'createElement').and.returnValue(fakeAnchor as HTMLAnchorElement);

    // Act: click Download on first row
    const btn = fixture.debugElement.queryAll(By.css('tbody tr button'))[1]; // second button is Download File for first row
    btn.nativeElement.click();
    tick();

    // Assert
    expect(svc.downloadImportFile).toHaveBeenCalledWith(1);
    expect(fakeAnchor.download).toBe('hist.csv');
    expect(fakeAnchor.click).toHaveBeenCalled();
  }));

  it('should show toast "File not found." when server returns 404', fakeAsync(() => {
    tick();
    const svc = TestBed.inject(ArchivesService) as any;
    spyOn(svc, 'downloadImportFile').and.returnValue(throwError(() => ({ status: 404 })));

    const btn = fixture.debugElement.queryAll(By.css('tbody tr button'))[1];
    btn.nativeElement.click();
    tick();
    fixture.detectChanges();

    const toast = fixture.debugElement.query(By.css('.fixed.bottom-4.right-4'));
    expect(toast).toBeTruthy();
    expect((toast.nativeElement as HTMLElement).textContent).toContain('File not found.');
  }));
});

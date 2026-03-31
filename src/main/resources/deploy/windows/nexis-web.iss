; ================================
; Nexis Web Installer
; ================================

[Setup]
AppName=Nexis Web
AppVersion=1.0
DefaultDirName={commonpf}\NexisWeb
DefaultGroupName=Nexis Web
OutputBaseFilename=NexisWeb_Setup_v1
Compression=lzma
SolidCompression=yes
PrivilegesRequired=admin
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64compatible
AllowRootDirectory=yes
AllowNoIcons=yes
DisableDirPage=no
SetupIconFile=icon.ico

[Files]
Source: "Nexis_Web.exe";          DestDir: "{app}"; Flags: ignoreversion
Source: "nexis-web.jar";          DestDir: "{app}"; Flags: ignoreversion
Source: "nexis.conf";             DestDir: "{app}"; Flags: ignoreversion
Source: "application.properties"; DestDir: "{app}"; Flags: ignoreversion
Source: "icon.ico";               DestDir: "{app}"; Flags: ignoreversion
Source: "icon.png";               DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\Nexis Web";       Filename: "{app}\Nexis_Web.exe"; IconFilename: "{app}\icon.ico"
Name: "{userdesktop}\Nexis Web"; Filename: "{app}\Nexis_Web.exe"; IconFilename: "{app}\icon.ico"

[UninstallDelete]
Type: files;          Name: "{app}\nexis-web.jar"
Type: files;          Name: "{app}\nexis.conf"
Type: files;          Name: "{app}\application.properties"
Type: filesandordirs; Name: "{app}"

[Code]
var
  WebPage, DbPage, ServerPage: TWizardPage;
  WebPortEdit: TEdit;
  DbTypeCombo: TComboBox;
  DbHostEdit, DbPortEdit, DbNameEdit, DbUserEdit, DbPassEdit: TEdit;
  ServerHostEdit, ServerPortEdit, ServerNameEdit: TEdit;
  LogDirEdit: TEdit;
  LogDirBtn: TButton;
  L: TLabel;

  G_WebPort: String;
  G_DbType, G_DbHost, G_DbPort, G_DbName, G_DbUser, G_DbPass: String;
  G_ServerHost, G_ServerPort, G_ServerName: String;
  G_LogDir: String;

function GetLogDir(Param: String): String;
begin
  Result := G_LogDir;
end;

procedure DbTypeComboChange(Sender: TObject);
begin
  case DbTypeCombo.ItemIndex of
    0: DbPortEdit.Text := '3306';
    1: DbPortEdit.Text := '3306';
    2: DbPortEdit.Text := '5432';
  else
    DbPortEdit.Text := '3306';
  end;
end;

procedure SetPropertyValue(Lines: TStringList; const Name, Value: String);
var
  i: Integer;
  Found: Boolean;
begin
  Found := False;
  for i := 0 to Lines.Count - 1 do
  begin
    if Pos(Name + '=', Lines[i]) = 1 then
    begin
      Lines[i] := Name + '=' + Value;
      Found := True;
      Break;
    end;
  end;
  if not Found then Lines.Add(Name + '=' + Value);
end;

procedure LogDirBtnClick(Sender: TObject);
var
  Dir: String;
begin
  Dir := LogDirEdit.Text;
  if not DirExists(Dir) then
    ForceDirectories(Dir);
  if BrowseForFolder('Select Log Directory', Dir, False) then
    LogDirEdit.Text := Dir;
end;

procedure InitializeWizard();
begin
  // ── Web 설정 페이지 ──
  WebPage := CreateCustomPage(wpSelectDir, 'Web Configuration', 'Configure web server settings');

  L := TLabel.Create(WizardForm);
  L.Parent := WebPage.Surface; L.Left := 10; L.Top := 13; L.Caption := 'Web Port:';

  WebPortEdit := TEdit.Create(WizardForm);
  WebPortEdit.Parent := WebPage.Surface;
  WebPortEdit.Left := 120; WebPortEdit.Top := 10; WebPortEdit.Width := 80;
  WebPortEdit.Text := '8080';

  L := TLabel.Create(WizardForm);
  L.Parent := WebPage.Surface; L.Left := 10; L.Top := 43; L.Caption := 'Log Directory:';

  LogDirEdit := TEdit.Create(WizardForm);
  LogDirEdit.Parent := WebPage.Surface;
  LogDirEdit.Left := 120; LogDirEdit.Top := 40; LogDirEdit.Width := 250;
  LogDirEdit.Text := ExpandConstant('{commonappdata}\NexisWeb\logs');

  LogDirBtn := TButton.Create(WizardForm);
  LogDirBtn.Parent := WebPage.Surface;
  LogDirBtn.Left := 375; LogDirBtn.Top := 38; LogDirBtn.Width := 75; LogDirBtn.Height := 23;
  LogDirBtn.Caption := 'Browse...';
  LogDirBtn.OnClick := @LogDirBtnClick;

  // ── DB 설정 페이지 ──
  DbPage := CreateCustomPage(WebPage.ID, 'Database Configuration', 'Configure database connection');

  L := TLabel.Create(WizardForm);
  L.Parent := DbPage.Surface; L.Left := 10; L.Top := 13; L.Caption := 'DB Type:';

  DbTypeCombo := TComboBox.Create(WizardForm);
  DbTypeCombo.Parent := DbPage.Surface;
  DbTypeCombo.Left := 120; DbTypeCombo.Top := 10; DbTypeCombo.Width := 150;
  DbTypeCombo.Style := csDropDownList;
  DbTypeCombo.Items.Add('MySQL');
  DbTypeCombo.Items.Add('MariaDB');
  DbTypeCombo.Items.Add('PostgreSQL');
  DbTypeCombo.ItemIndex := 0;
  DbTypeCombo.OnChange := @DbTypeComboChange;

  L := TLabel.Create(WizardForm);
  L.Parent := DbPage.Surface; L.Left := 10; L.Top := 43; L.Caption := 'DB Host:';
  DbHostEdit := TEdit.Create(WizardForm);
  DbHostEdit.Parent := DbPage.Surface; DbHostEdit.Left := 120; DbHostEdit.Top := 40; DbHostEdit.Width := 200;
  DbHostEdit.Text := '127.0.0.1';

  L := TLabel.Create(WizardForm);
  L.Parent := DbPage.Surface; L.Left := 10; L.Top := 73; L.Caption := 'DB Port:';
  DbPortEdit := TEdit.Create(WizardForm);
  DbPortEdit.Parent := DbPage.Surface; DbPortEdit.Left := 120; DbPortEdit.Top := 70; DbPortEdit.Width := 80;
  DbPortEdit.Text := '3306';

  L := TLabel.Create(WizardForm);
  L.Parent := DbPage.Surface; L.Left := 10; L.Top := 103; L.Caption := 'DB Name:';
  DbNameEdit := TEdit.Create(WizardForm);
  DbNameEdit.Parent := DbPage.Surface; DbNameEdit.Left := 120; DbNameEdit.Top := 100; DbNameEdit.Width := 200;
  DbNameEdit.Text := 'nexis';

  L := TLabel.Create(WizardForm);
  L.Parent := DbPage.Surface; L.Left := 10; L.Top := 133; L.Caption := 'Username:';
  DbUserEdit := TEdit.Create(WizardForm);
  DbUserEdit.Parent := DbPage.Surface; DbUserEdit.Left := 120; DbUserEdit.Top := 130; DbUserEdit.Width := 200;

  L := TLabel.Create(WizardForm);
  L.Parent := DbPage.Surface; L.Left := 10; L.Top := 163; L.Caption := 'Password:';
  DbPassEdit := TEdit.Create(WizardForm);
  DbPassEdit.Parent := DbPage.Surface; DbPassEdit.Left := 120; DbPassEdit.Top := 160; DbPassEdit.Width := 200;
  DbPassEdit.PasswordChar := '*';

  // ── Nexis Server 설정 페이지 ──
  ServerPage := CreateCustomPage(DbPage.ID, 'Nexis Server Connection', 'Configure Nexis Server connection');

  L := TLabel.Create(WizardForm);
  L.Parent := ServerPage.Surface; L.Left := 10; L.Top := 13; L.Caption := 'Server Host:';
  ServerHostEdit := TEdit.Create(WizardForm);
  ServerHostEdit.Parent := ServerPage.Surface; ServerHostEdit.Left := 120; ServerHostEdit.Top := 10; ServerHostEdit.Width := 200;
  ServerHostEdit.Text := '127.0.0.1';

  L := TLabel.Create(WizardForm);
  L.Parent := ServerPage.Surface; L.Left := 10; L.Top := 43; L.Caption := 'Server Port:';
  ServerPortEdit := TEdit.Create(WizardForm);
  ServerPortEdit.Parent := ServerPage.Surface; ServerPortEdit.Left := 120; ServerPortEdit.Top := 40; ServerPortEdit.Width := 80;
  ServerPortEdit.Text := '9000';

  L := TLabel.Create(WizardForm);
  L.Parent := ServerPage.Surface; L.Left := 10; L.Top := 73; L.Caption := 'Server Name:';
  ServerNameEdit := TEdit.Create(WizardForm);
  ServerNameEdit.Parent := ServerPage.Surface; ServerNameEdit.Left := 120; ServerNameEdit.Top := 70; ServerNameEdit.Width := 200;
  ServerNameEdit.Text := 'Nexis Server';
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;

  if CurPageID = WebPage.ID then
  begin
    if WebPortEdit.Text = '' then
    begin
      MsgBox('Please enter Web Port.', mbError, MB_OK);
      Result := False; Exit;
    end;
    G_WebPort := WebPortEdit.Text;
    G_LogDir  := LogDirEdit.Text;
    if G_LogDir = '' then G_LogDir := ExpandConstant('{commonappdata}\NexisWeb\logs');
  end;

  if CurPageID = DbPage.ID then
  begin
    if DbHostEdit.Text = '' then begin MsgBox('Please enter DB Host.',     mbError, MB_OK); Result := False; Exit; end;
    if DbPortEdit.Text = '' then begin MsgBox('Please enter DB Port.',     mbError, MB_OK); Result := False; Exit; end;
    if DbNameEdit.Text = '' then begin MsgBox('Please enter DB Name.',     mbError, MB_OK); Result := False; Exit; end;
    if DbUserEdit.Text = '' then begin MsgBox('Please enter DB Username.', mbError, MB_OK); Result := False; Exit; end;
    G_DbType := DbTypeCombo.Text;
    G_DbHost := DbHostEdit.Text;
    G_DbPort := DbPortEdit.Text;
    G_DbName := DbNameEdit.Text;
    G_DbUser := DbUserEdit.Text;
    G_DbPass := DbPassEdit.Text;
  end;

  if CurPageID = ServerPage.ID then
  begin
    if ServerHostEdit.Text = '' then begin MsgBox('Please enter Server Host.', mbError, MB_OK); Result := False; Exit; end;
    if ServerPortEdit.Text = '' then begin MsgBox('Please enter Server Port.', mbError, MB_OK); Result := False; Exit; end;
    G_ServerHost := ServerHostEdit.Text;
    G_ServerPort := ServerPortEdit.Text;
    G_ServerName := ServerNameEdit.Text;
    if G_ServerName = '' then G_ServerName := 'Nexis Server';
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  AppDir, AppDirFwd, ConfFile, PropFile, LogPath: String;
  Lines: TStringList;
begin
  if CurStep = ssPostInstall then
  begin
    AppDir   := ExpandConstant('{app}');
    ConfFile := AppDir + '\nexis.conf';
    PropFile := AppDir + '\application.properties';

    // 슬래시 변환
    AppDirFwd := AppDir;
    StringChangeEx(AppDirFwd, '\', '/', True);
    LogPath := G_LogDir;
    StringChangeEx(LogPath, '\', '/', True);

    // ── nexis.conf 업데이트 ──
    Lines := TStringList.Create;
    try
      if FileExists(ConfFile) then
        Lines.LoadFromFile(ConfFile);
      SetPropertyValue(Lines, 'db.type',     G_DbType);
      SetPropertyValue(Lines, 'db.host',     G_DbHost);
      SetPropertyValue(Lines, 'db.port',     G_DbPort);
      SetPropertyValue(Lines, 'db.name',     G_DbName);
      SetPropertyValue(Lines, 'db.user',     G_DbUser);
      SetPropertyValue(Lines, 'db.password', G_DbPass);
      SetPropertyValue(Lines, 'server.host', G_ServerHost);
      SetPropertyValue(Lines, 'server.port', G_ServerPort);
      SetPropertyValue(Lines, 'server.name', G_ServerName);
      SetPropertyValue(Lines, 'installed',   'false');
      Lines.SaveToFile(ConfFile);
    finally
      Lines.Free;
    end;

    // ── application.properties 업데이트 ──
    Lines := TStringList.Create;
    try
      if FileExists(PropFile) then
        Lines.LoadFromFile(PropFile);
      SetPropertyValue(Lines, 'server.port',       G_WebPort);
      SetPropertyValue(Lines, 'nexis.config.path', AppDirFwd + '/nexis.conf');
      SetPropertyValue(Lines, 'logging.file.name', LogPath + '/nexis-web.log');
      Lines.SaveToFile(PropFile);
    finally
      Lines.Free;
    end;

    // ── 로그 디렉토리 생성 ──
    if not DirExists(G_LogDir) then
      ForceDirectories(G_LogDir);
  end;
end;

[Run]
Filename: "cmd.exe"; Parameters: "/c mkdir ""{code:GetLogDir}"""; Flags: runhidden nowait
Filename: "{app}\Nexis_Web.exe"; Description: "Launch Nexis Web after install"; Flags: nowait postinstall skipifsilent shellexec; Verb: "runas"
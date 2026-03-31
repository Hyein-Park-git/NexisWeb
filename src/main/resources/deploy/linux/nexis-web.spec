Name:           nexis-web
Version:        1.0
Release:        1
Summary:        Nexis Web - Monitoring Platform Web Interface
License:        Proprietary
BuildArch:      noarch

%description
Nexis Web provides the web interface for the Nexis Monitoring Platform.

%install
mkdir -p %{buildroot}/opt/nexis-web
mkdir -p %{buildroot}/etc/systemd/system
mkdir -p %{buildroot}/var/log/nexis-web

install -m 0755 %{_sourcedir}/nexis-web.jar          %{buildroot}/opt/nexis-web/nexis-web.jar
install -m 0644 %{_sourcedir}/nexis.conf              %{buildroot}/opt/nexis-web/nexis.conf
install -m 0644 %{_sourcedir}/application.properties  %{buildroot}/opt/nexis-web/application.properties
install -m 0644 %{_sourcedir}/nexis-web.service       %{buildroot}/etc/systemd/system/nexis-web.service

%files
%dir /opt/nexis-web
%dir /var/log/nexis-web
/opt/nexis-web/nexis-web.jar
%config(noreplace) /opt/nexis-web/nexis.conf
%config(noreplace) /opt/nexis-web/application.properties
/etc/systemd/system/nexis-web.service

%pre
echo "[nexis-web] Pre-install starting..."
if ! command -v java &> /dev/null; then
    echo "[nexis-web] ERROR: Java is not installed. Please install Java 21 or higher."
    exit 1
fi
echo "[nexis-web] Java check passed."

%post
CONF=/opt/nexis-web/nexis.conf
CONFIGURED=0

cancel_install() {
    stty echo 2>/dev/null
    echo ""
    echo "[nexis-web] Installation cancelled."
    exit 0
}

trap cancel_install INT TERM

if [ -e /dev/tty ]; then
    exec < /dev/tty

    echo "========================================"
    echo "  Nexis Web Configuration"
    echo "  (Press Ctrl+C to cancel at any time)"
    echo "========================================"

    echo ""
    printf "Web Port [8080]: "
    read WEB_PORT < /dev/tty || cancel_install
    WEB_PORT=${WEB_PORT:-8080}

    echo ""
    echo "Select DB type:"
    echo "  1) MySQL"
    echo "  2) MariaDB"
    echo "  3) PostgreSQL"
    printf "Enter choice [1-3] (default: 1): "
    read DB_TYPE_NUM < /dev/tty || cancel_install
    case $DB_TYPE_NUM in
        2) DB_TYPE="MariaDB"    ; DEFAULT_DB_PORT="3306" ;;
        3) DB_TYPE="PostgreSQL" ; DEFAULT_DB_PORT="5432" ;;
        *) DB_TYPE="MySQL"      ; DEFAULT_DB_PORT="3306" ;;
    esac

    printf "DB Host [127.0.0.1]: "
    read DB_HOST < /dev/tty || cancel_install
    DB_HOST=${DB_HOST:-127.0.0.1}

    printf "DB Port [$DEFAULT_DB_PORT]: "
    read DB_PORT < /dev/tty || cancel_install
    DB_PORT=${DB_PORT:-$DEFAULT_DB_PORT}

    printf "DB Name [nexis]: "
    read DB_NAME < /dev/tty || cancel_install
    DB_NAME=${DB_NAME:-nexis}

    printf "DB Username: "
    read DB_USER < /dev/tty || cancel_install
    if [ -z "$DB_USER" ]; then
        echo "[nexis-web] WARNING: DB Username is empty."
    fi

    printf "DB Password: "
    stty -echo 2>/dev/null
    read DB_PASS < /dev/tty || { stty echo 2>/dev/null; cancel_install; }
    stty echo 2>/dev/null
    echo ""

    echo ""
    echo "--- Nexis Server Connection ---"
    printf "Nexis Server Host [127.0.0.1]: "
    read SERVER_HOST < /dev/tty || cancel_install
    SERVER_HOST=${SERVER_HOST:-127.0.0.1}

    printf "Nexis Server Port [9000]: "
    read SERVER_PORT < /dev/tty || cancel_install
    SERVER_PORT=${SERVER_PORT:-9000}

    printf "Nexis Server Name [Nexis Server]: "
    read SERVER_NAME < /dev/tty || cancel_install
    SERVER_NAME=${SERVER_NAME:-"Nexis Server"}

    printf "Log directory [/var/log/nexis-web]: "
    read LOG_DIR < /dev/tty || cancel_install
    LOG_DIR=${LOG_DIR:-/var/log/nexis-web}

    echo ""
    echo "========================================"
    echo "  Configuration Summary"
    echo "========================================"
    echo "  Web Port    : $WEB_PORT"
    echo "  DB Type     : $DB_TYPE"
    echo "  DB Host     : $DB_HOST"
    echo "  DB Port     : $DB_PORT"
    echo "  DB Name     : $DB_NAME"
    echo "  DB Username : $DB_USER"
    echo "  DB Password : ********"
    echo "  Server Host : $SERVER_HOST"
    echo "  Server Port : $SERVER_PORT"
    echo "  Server Name : $SERVER_NAME"
    echo "  Log Dir     : $LOG_DIR"
    echo "========================================"
    printf "Apply this configuration? [Y/n]: "
    read CONFIRM < /dev/tty || cancel_install
    if [ "$CONFIRM" = "n" ] || [ "$CONFIRM" = "N" ]; then
        cancel_install
    fi

    # ✅ nexis.conf - sed로 값만 교체 (주석 유지)
    sed -i "s|^db.type=.*|db.type=$DB_TYPE|"         $CONF
    sed -i "s|^db.host=.*|db.host=$DB_HOST|"         $CONF
    sed -i "s|^db.port=.*|db.port=$DB_PORT|"         $CONF
    sed -i "s|^db.name=.*|db.name=$DB_NAME|"         $CONF
    sed -i "s|^db.user=.*|db.user=$DB_USER|"         $CONF
    sed -i "s|^db.password=.*|db.password=$DB_PASS|" $CONF
    sed -i "s|^server.host=.*|server.host=$SERVER_HOST|" $CONF
    sed -i "s|^server.port=.*|server.port=$SERVER_PORT|" $CONF
    sed -i "s|^server.name=.*|server.name=$SERVER_NAME|" $CONF
    sed -i "s|^installed=.*|installed=false|"         $CONF
    chmod 640 $CONF

    # ✅ application.properties - sed로 값만 교체
    sed -i "s|^server.port=.*|server.port=$WEB_PORT|" /opt/nexis-web/application.properties
    sed -i "s|^logging.file.name=.*|logging.file.name=$LOG_DIR/nexis-web.log|" /opt/nexis-web/application.properties
    sed -i "s|^nexis.config.path=.*|nexis.config.path=/opt/nexis-web/nexis.conf|" /opt/nexis-web/application.properties

    mkdir -p $LOG_DIR
    chmod 755 $LOG_DIR

    CONFIGURED=1

else
    echo "[nexis-web] No interactive terminal detected."
    echo "[nexis-web] Please edit /opt/nexis-web/nexis.conf before starting."
    CONFIGURED=1
fi

trap - INT TERM

if [ $CONFIGURED -eq 1 ]; then
    systemctl daemon-reload
    systemctl enable nexis-web

    echo ""
    echo "[nexis-web] Installation complete."
    echo "[nexis-web] Review config : vi /opt/nexis-web/nexis.conf"
    echo "[nexis-web] To start      : systemctl start nexis-web"
    echo "[nexis-web] To check      : systemctl status nexis-web"
    echo "[nexis-web] Access        : http://localhost:$WEB_PORT/install"
    echo "========================================"
fi

%preun
if [ $1 -eq 0 ]; then
    systemctl stop nexis-web 2>/dev/null || true
    systemctl disable nexis-web 2>/dev/null || true
    systemctl daemon-reload
fi

%postun
# nothing

%changelog
* Thu Jan 01 2026 Nexis <nexis@example.com> - 1.0-1
- Initial release
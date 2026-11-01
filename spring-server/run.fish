cd ~/BMS/spring-server/
set -gx JWT_SECRET 'lL6wW7gYOyezvFDVCuywg2Vo/oQOVwXvbjYSTPfKjMM='
set -gx DB_USERNAME bms_app
read -s -P "Enter the bms_app password: " DB_PASSWORD
echo
set -gx DB_PASSWORD $DB_PASSWORD

mvn spring-boot:run

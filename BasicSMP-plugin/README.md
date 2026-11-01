# BasicSMP (plugin only)

Plugin for Paper 1.21.4 (Java 21). Package: com.jitnation.basicsmp

Commands: /tpa, /tpahere, /tpaccept, /tpdeny, /sethome, /delhome, /home, /homes,
/strength, /enchantrotation (admin). Settings: plugins/BasicSMP/config.yml (created on first start).

## Get the jar
Option 1 - GitHub (no installs): create a GitHub repo, upload everything in this folder,
open the Actions tab, wait for "Build plugin jar", download the "BasicSMP" artifact,
unzip it -> BasicSMP-1.0.0.jar.

Option 2 - your PC: install JDK 21 + Maven, run `mvn package`
-> target/BasicSMP-1.0.0.jar

## Install
Put BasicSMP-1.0.0.jar in your server's plugins/ folder and restart.
Console should print: [BasicSMP] BasicSMP enabled

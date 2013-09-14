#!/bin/bash
buildId=`cat ./buildId`
echo "Build number $buildId"

#rm -r reobf/
#mkdir reobf/

#rm -r core/
#mkdir -p core/rainwarrior/hooks/

rm -r mod/
mkdir -p mod/rainwarrior/

#unzip -q output.jar -d reobf/
#(cd ..; rm -r ./bin/minecraft/argo; ./reobfuscate_srg.sh)
#cp -r ../reobf/minecraft/* reobf/

#cp reobf/rainwarrior/hooks/Plugin* core/rainwarrior/hooks/
#cp reobf/rainwarrior/hooks/Transformer* core/rainwarrior/hooks/
#cp LICENSE COPYING README core/
#(cd core/; jar -cm ../MANIFEST.MF > ../TrussCore-beta-${buildId}.jar .)

mkdir -p mod/assets/
cp -r ../assets/trussmod mod/assets/

cp reobf/rainwarrior/*.class mod/rainwarrior/
cp -r reobf/rainwarrior/trussmod/ mod/rainwarrior/
cp -r reobf/rainwarrior/hooks/ mod/rainwarrior/
cp -r reobf/gnu/ mod/
#rm mod/rainwarrior/hooks/Plugin*
#rm mod/rainwarrior/hooks/Transformer*
cp LICENSE COPYING README mod/
cat mcmod.info | sed "s/\$buildId/$buildId/" > mod/mcmod.info

(cd mod/; jar -cm ../MANIFEST.MF > ../TrussMod-beta-1.6.2-${buildId}.jar .)

#zip TrussMod-beta-$buildId-archive.zip \
#	TrussCore-beta-${buildId}.jar \
#	TrussMod-beta-${buildId}.jar \
#	LICENSE COPYING README

#rm TrussCore-beta-${buildId}.jar
#rm TrussMod-beta-${buildId}.jar

nextId=`expr $buildId + 1`
echo $nextId > ./buildId

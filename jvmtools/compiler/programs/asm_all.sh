
set -e
for file in *.asm
do
    echo =============================================================
    echo $file
    echo =============================================================
    ./asm.sh $file
done

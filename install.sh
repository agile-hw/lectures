python3 -m pip install virtualenv
python3 -m virtualenv chisel_nb_env
source chisel_nb_env/bin/activate
pip3 install jupyterlab
pip3 install jupyter_contrib_nbextensions
pip3 install jupyter_nbextensions_configurator
pip3 install RISE
jupyter nbextension enable splitcell/splitcell
curl -L -o coursier https://git.io/coursier-cli && chmod +x coursier
SCALA_VERSION=2.13.4 ALMOND_VERSION=0.11.1
./coursier bootstrap -r jitpack \
    -i user -I user:sh.almond:scala-kernel-api_$SCALA_VERSION:$ALMOND_VERSION \
    sh.almond:scala-kernel_$SCALA_VERSION:$ALMOND_VERSION \
    --sources --default=true \
    -o almond
./almond --install
rm almond
rm coursier

echo '\n\n\nTo enter virtualenv type: source chisel_nb_env/bin/activate'

echo '\nIf going to use verification, install z3 on your platform (e.g. sudo apt install z3)'

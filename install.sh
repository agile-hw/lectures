python3 -m pip install virtualenv
python3 -m virtualenv chisel_nb_env
source chisel_nb_env/bin/activate
# Insist on old Jupyter to splitcell support
pip3 install jupyterlab==3.6.6
pip3 install jupyter_contrib_nbextensions
pip3 install jupyter_nbextensions_configurator
pip3 install RISE
jupyter nbextension enable splitcell/splitcell

# If the extensions, in particular splitcell aren't fully working, consider:
# jupyter nbextension install jupyter_contrib_nbextensions --py --sys-prefix
# jupyter nbextension install jupyter_nbextensions_configurator --py --sys-prefix
# jupyter nbextension enable splitcell/splitcell

curl -fLo coursier https://github.com/coursier/launchers/raw/master/coursier && chmod +x coursier
SCALA_VERSION=2.13.14 ALMOND_VERSION=0.14.0-RC15
./coursier bootstrap -r jitpack \
    -i user -I user:sh.almond:scala-kernel-api_$SCALA_VERSION:$ALMOND_VERSION \
    sh.almond:scala-kernel_$SCALA_VERSION:$ALMOND_VERSION \
    --sources --default=true \
    -o almond
./almond --install
# If you have multiple Jupyter's you can use --jupyter-path to guide it
rm almond
rm coursier

# If you get an odd permissions issue from Jupyter, you can disable auth.
jupyter notebook --NotebookApp.token=''

echo '\n\n\nTo enter virtualenv type: source chisel_nb_env/bin/activate'

echo '\nIf going to use verification, install z3 on your platform (e.g. sudo apt install z3)'

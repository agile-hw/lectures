# install virtualenv
apt install python3-virtualenv

# make virtualenv for lecture environment
python3 -m venv chisel_nb_env
source chisel_nb_env/bin/activate
# Insist on old Jupyter to keep support for splitcell
pip3 install jupyterlab==3.6.6
pip3 install RISE
pip3 install jupyter_contrib_nbextensions
jupyter nbextension enable splitcell/splitcell

# If the extensions, in particular splitcell aren't fully working, consider:
# jupyter nbextension install jupyter_contrib_nbextensions --py --sys-prefix
# jupyter nbextension enable splitcell/splitcell

# install almond (Scala in Jupyter)
curl -fLo coursier https://github.com/coursier/launchers/raw/master/coursier && chmod +x coursier
SCALA_VERSION=2.13.14 ALMOND_VERSION=0.14.0-RC15
./coursier bootstrap -r jitpack \
    -i user -I user:sh.almond:scala-kernel-api_$SCALA_VERSION:$ALMOND_VERSION \
    sh.almond:scala-kernel_$SCALA_VERSION:$ALMOND_VERSION \
    --sources --default=true \
    -o almond
# If you have multiple Jupyter's you can use --jupyter-path to guide it
./almond --install
# Once installed, no longer need installers
rm almond coursier

# If you get an odd permissions issue from Jupyter, you can disable auth.
# jupyter notebook --NotebookApp.token=''

echo '\n\n\nTo enter virtualenv type: source chisel_nb_env/bin/activate'

echo '\nIf going to use verification, install z3 on your platform (e.g. sudo apt install z3)'

interface NavigationContent extends React.PropsWithChildren {}

const ColumnsContainer: React.FC<NavigationContent> = ({children}) => {
     return <div
     className="w-full flex flex-row flex-nowrap overflow-auto"
    >
        {children}
    </div>
}

export default ColumnsContainer
